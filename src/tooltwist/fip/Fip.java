package tooltwist.fip;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.Vector;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import tooltwist.fip.FipDelta.Type;
import tooltwist.fip.FipRule.Op;
import tooltwist.fip.FipBatchOfUpdates.BufferStatus;

/*
 * TODO
 * - CHECK...... Don't give file list unless the properties and credentials check out.
 * - encrypt initial sourceUuid request
 * - individual file commits
 * - plugins for include/exclude/ignore
 * - ipaddr whitelist or blacklist
 * - rollbacks.
 * - mechanism to create the initial FIP config files (generate uuids, etc)
 * - option to flush the manifest files.
 * - remove rollback files when a new transaction is started.
 * - progress meter. X
 * - handle directories - deleting and also adding empty directories.
 * - don't show stack trace when a server connect fails.
 * - tidy up.
 * - display count of installs and deletes.
 * 
 * ONE DAY:
 * - parallel download and upload.
 * - option to get remote source to contact destination directly.
 * - have destination "standby" files - big files that might be required.
 * 
 * TO TEST:
 * - trying to commit/abort a transaction still being prepared
 * - transferring to remote
 * - transferring FROM remote
 * - local status
 * - remote status
 * - pre and post commit Tomcat operations
 * - transferring files after expire
 * - commit after expire
 * - abort after expire
 * - each commit mode
 * - check statuses for failed installs (incremental, and transactional)
 * 
 * DONE:
 * - rename batch as "transaction" across all of FIP
 * - log the updates
 * - log the request
 * - store understudy files in a shadow directory tree.
 * - remove the "install" and "download" code.
 * - specify the commit mode in the destination properties file
 * - check that the properties files are owned by the current user
 * - Check that the current user is not root.
 * - deletes must originate from the source
 * - set execute flag on executables
 * - When asked for updates and delete commands, the source must confirm against the manifest.
 * - options to start/stop a main Tomcat instance
 * - protecting files / directories
 */
/**
 * This program has the ability to act as a third party installer. That is, the source
 * is on a remote machine, and the destination is a remote machine, where each of the
 * remote machines is running an instance of FIP.
 * 
 * To provide security, it is necessary to confirm that the source has permission to
 * send files to the destination. This machine is not important in that process, although
 * it does decide which files need to be transferred. Each machine has a unique id (uuid).
 * 
 * Setup on destination:
 * 			Before this process can be performed, a .fip-destination file must placed in the destination top level
 * 			folder, containing uuid and passphrase properties. The id should be generated using the uuidgen command.
 *
 * Setup on source:
 * 			A .fip-source file, containing a uuid property, and multiple lines of <destinationUuid>=<passPhrase>.
 * 
 * Step 1. Ask the source for it's uuid.
 * 
 * Step 2. Fip contacts the destination with a "new transaction" request, along with the source uuid, and gets the
 * 			destination uuid, a transaction number and also a random value called a "salt". A directory is created for
 * 			the transaction, named .fip-xxx-<txId>, where xxx is the transaction status, and a properties file is created
 * 			in that directory named .fip-txprops, containing the salt, the request's ip address, and an expiry time.
 * 
 * Step 3. Ask the source for a list of files to be installed.
 * 
 * Step 4. Ask the destination for a list of files already installed.
 * 
 * Step 5. The uuid, transaction number and salt are passed when asking the source for files to pass to the
 * 			destination. The returned list of updates are signed with an SHA1 hash generated using the txId,
 * 			salt, the passphrase, and the contents of the update buffer.
 * 
 * Step 6. The updates are passed by Fip to to the destination server unmodified. On the server, the
 * 			signature is compared to it's own calculated signature.
 * 
 * Step 7. The destination randomly chooses a new salt, and sends it in the reply.
 * 
 *  Note that this scheme does not encrypt the data being transferred. It merely checks that the source
 *  is allowed to publish to the destination, and guarantees the updates are not modified en-route. Even
 *  a hacked version of this program is unable to modify transferred files.
 *  
 *  To Test, use the following arguments:
 *  Local only:
 *  	/Users/philipcallender/Stuff/fip_src /Users/philipcallender/Stuff/fip_dst
 *  Remote source:
 *  	http://wagtail.local:39393/Users/philipcallender/Stuff/fip_src /Users/philipcallender/Stuff/fip_dst
 *  Remote destination:
 *  	/Users/philipcallender/Stuff/fip_src http://wagtail.local:39393/Users/philipcallender/Stuff/fip_dst
 * 
 * @author philipcallender
 *
 */
public class Fip
{
	public static byte MAJOR_VERSION_NUMBER = 0x01;
	public static byte MINOR_VERSION_NUMBER = 0x03;	
	private Vector<FipRule> rules = new Vector<FipRule>();

	/**
	 * The prefix used by all fip's files.
	 */
	public static final String PREFIX = ".fip-";
	
	public void install(String relativePathPattern)
	{
		FipRule rule = new FipRule_include(relativePathPattern);
		rules.add(rule);
	}

	public void dontInstall(String relativePathPattern)
	{
		FipRule rule = new FipRule_dontInstall(relativePathPattern);
		rules.add(rule);
	}

	public void ignore(String relativePathPattern)
	{
		FipRule rule = new FipRule_ignore(relativePathPattern);
		rules.add(rule);
	}

	public void map(String sourceRelativePath, String destinationRelativePath)
	{
		dontInstall(destinationRelativePath); // Ignore any file going to the same place
		install(sourceRelativePath);
		FipRule rule = new FipRule_map(sourceRelativePath, destinationRelativePath);
		rules.add(rule);
	}

	public void mapDirectory(String pathPrefix, String newPathPrefix) throws FipException
	{
		FipRule rule = new FipRule_mapDirectory(pathPrefix, newPathPrefix);
		rules.add(rule);
	}

	public void rule(FipRule rule)
	{
		rules.add(rule);
	}

	public void installFiles(String sourceUrl, String destinationUrl, boolean debugMessages, boolean verbose, boolean listOnly) throws FipException, IOException, FipCorruptionException
	{
		FipServerProxy source = getServerProxy(sourceUrl);
		FipServerProxy destination = getServerProxy(destinationUrl);
		
		// Verify the machines can talk:
		// Get uuid from the source.
		// Get uuid from the destination, and open a transaction (unless in listOnly mode).
		String sourceUuid = source.askForUuid();
		NewTransactionReply reply = destination.startNewTransaction(sourceUuid);
		String destinationUuid = reply.getDestinationUuid();
		String txId = reply.getTxId();
		String salt = reply.getSalt();
		
		

		// Load existing definition from the source.
//		System.out.println("\nClient list=");
		System.out.println("Indexing source...");
		FipList filesAtSource = source.askForFileList(false);

		// Check the rules against each source file.
		if ( !rules.isEmpty())
		{
			
			for (FipFile f : filesAtSource.files())
			{
				f.setOp(Op.EXCLUDE); // Exclude it until told otherwise.
				for (FipRule r : rules)
				{
					r.setRuleParametersForFile(f);
				}
			}
		}
		if (debugMessages)
		{
			String list = filesAtSource.serialize(true);
			System.out.println("Files at source:\n" + list);
		}
		
		// Get the existing files at the destination
		System.out.println("Indexing destination...");
		FipList filesAtDestination = destination.askForFileList(true);
		if (debugMessages)
		{
			String list = filesAtDestination.serialize(false);
			System.out.println("Files at destination:\n" + list);
		}

		System.out.println("Comparing...");
		FipDeltaList deltaList = filesAtSource.getDelta(filesAtDestination);
//		if (verbose || listOnly)
		{
			String deltaDesc = deltaList.listDeltas();
			System.out.println("Delta:\n" + deltaDesc);
		}
		
		if (listOnly)
			return;
		
		if ( !deltaList.areChanges())
			return;
		
//		String txId = deltaList.gettxId();
//		String salt = deltaList.getInitialSalt();
		
		// Calculate the approximate total size of the updates
		System.out.println("Calculating size...");
		FipBufferCapacityCalculator bcc = new FipBufferCapacityCalculator();
		long estimatedTotalSize = bcc.spaceRequiredAtStartOfBuffer();
//int cnt = 1;
		for (FipDelta d : deltaList.list())
		{
//			String sourceRelativePath = d.getSourceRelativePath();
			String destinationRelativePath = d.getDestinationRelativePath();
			long fileLen = d.getFilesize();

			// See how much space is required in the buffer
			long spaceRequiredInBuffer = 0;
			if (d.getType() == Type.NEW || d.getType() == Type.CHANGE)
				spaceRequiredInBuffer = bcc.spaceRequiredForUpdate(destinationRelativePath, fileLen);
			else if (d.getType() == Type.DELETE)
				spaceRequiredInBuffer = bcc.spaceRequiredForDelete(destinationRelativePath);
			estimatedTotalSize += spaceRequiredInBuffer;
//bcc.willItFit(spaceRequiredInBuffer);
//System.out.println("  after1 " + cnt++ + " total is " + estimatedTotalSize + " --- " + bcc.getSpaceUsed());
//if (cnt==72)
//{
//bcc.resetCounter();
//estimatedTotalSize = 0;
//}
		}
		int estimatedBatches = (int) (estimatedTotalSize / FipBatchOfUpdates.PREFERRED_MAX_TRANSMISSION) + 1;
		estimatedTotalSize += estimatedBatches * (bcc.spaceRequiredAtStartOfBuffer() + bcc.spaceRequiredForCommit());
		if (estimatedTotalSize > 5000)
			System.out.println("Estimated total size of updates = "+sizeFmt(estimatedTotalSize));
		System.out.println("Starting transmission...");

		// Loop around, getting updates from the source and sending them to the destination, until there are none left
		FipRequestList requestList = new FipRequestList();
//		Vector<String> deleteList = new Vector<String>();
		long totalSent = 0;
//long zzz = 0;
//cnt = 1;
		boolean areChanges = false;
		try {
int philDeltaCount = 0;
			bcc = new FipBufferCapacityCalculator();
			int cntInstall = 0;
			int cntDelete = 0;
			int cntBundle = 1;
			for (FipDelta d : deltaList.list())
			{
philDeltaCount++;
				String sourceRelativePath = d.getSourceRelativePath();
				String destinationRelativePath = d.getDestinationRelativePath();
				long fileLen = d.getFilesize();
	
				// See how much space is required in the buffer
				long spaceRequiredInBuffer = 0;
				if (d.getType() == Type.NEW || d.getType() == Type.CHANGE)
					spaceRequiredInBuffer = bcc.spaceRequiredForUpdate(destinationRelativePath, fileLen);
				else if (d.getType() == Type.DELETE)
					spaceRequiredInBuffer = bcc.spaceRequiredForDelete(destinationRelativePath);


				// If this update won't fit in the buffer, send what we have so far.
				BufferStatus willItFit = bcc.willItFit(spaceRequiredInBuffer);

System.out.println(philDeltaCount + ": "+sourceRelativePath);				
System.out.println("  start="+bcc.getSpaceUsed() + ", size= " + spaceRequiredInBuffer + " listSize=" + requestList.size());				
//zzz += spaceRequiredInBuffer;
//System.out.println("  after2 " + cnt++ + " total is " + zzz + " --- " + bcc.getSpaceUsed());
				switch (willItFit)
				{
				case WILL_FIT:
System.out.println("  will fit");
					// Cool.
					break;
					
				case WILL_NOT_FIT:
System.out.println("  will not fit");
					// This file won't fit on top of what's already there, so send the buffer, then add it.
					// Get a list of updates from the source.
					// Add on the deletes.
					// Send to the destination server.
//					int usedInThisBundle = bcc.getSpaceUsed();
					int used = bcc.getSpaceUsed();
					System.out.print("  Bundle " + cntBundle++ + " (" + sizeFmt(used) + " = " + cntInstall + " installs, " + cntDelete + " deletes)");
System.out.println("\n\n\nvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv\n\n");
philDeltaCount = 1;
System.out.println("\n\n" + philDeltaCount + ": "+sourceRelativePath);				
System.out.println("  start="+bcc.getSpaceUsed() + ", size= " + spaceRequiredInBuffer + " listSize=" + requestList.size() + "\n\n\n");				
					processUpdatesAndDeletes(source, destination, requestList, destinationUuid, txId, salt, false);
					cntInstall = 0;
					cntDelete = 0;
					
//					totalSent += bcc.getSpaceUsed();
					long perc = (100 * totalSent) / estimatedTotalSize;
					if (verbose)
						System.out.print("\n  Total sent="+ sizeFmt(totalSent));
					System.out.println("  ..."+perc +"%");
					if (verbose)
						System.out.println();
//System.out.println("Total sent so far = "+ totalSent);

					bcc.resetCounter();
System.out.println("\n\n\n\n\n-----------------------------------------------------------------------------------------\n\n\n\n\n");
					break;
					
				case IMPOSSIBLE_TO_SEND_BIGGER_THAN_BUFFER:
System.out.println("  bigger than buffer");
					// This file can never be sent - bomb out
					throw new FipException("File is too large to be downloaded: " + sourceRelativePath);
				}
	
				// Now add the operation to the buffer
				if (d.getType() == Type.NEW || d.getType() == Type.CHANGE)
				{
					requestList.addRequestForInstall(sourceRelativePath, destinationRelativePath);
					areChanges = true;
					totalSent += spaceRequiredInBuffer;
					cntInstall++;
				}
				else if (d.getType() == Type.DELETE)
				{
					//NOTE: TO BE SECURE, THIS DELETE MUST BE ADDED TO THE INSTRUCTIONS FROM THE SOURCE SERVER. SO:
					// 1. THIS PROGRAM MUST SEND A REQUEST TO THE SOURCE SERVER TO ADD THE DELETE COMMAND, SIMILAR TO THE INSTALL OPERATIONS.
					// 2. THE SOURCE SHOULD CONFIRM THAT THE FILE HAS ACTUALLY BEEN DELETED.
					requestList.addRequestForDelete(destinationRelativePath);
					areChanges = true;
					totalSent += spaceRequiredInBuffer;
					cntDelete++;
				}
			}
			
			requestList.addRequestForEndOfTransactionMarker();
			
			if (requestList.size() > 0)
			{
				int used = bcc.getSpaceUsed();
				System.out.println("  Bundle " + cntBundle++ + " (" + sizeFmt(used) + " = "+ cntInstall + " installs, " + cntDelete + " deletes)");
				processUpdatesAndDeletes(source, destination, requestList, destinationUuid, txId, salt, areChanges);
				bcc.resetCounter();
//				System.out.println("  ...100%");
				System.out.println("Total sent = "+ sizeFmt(totalSent));
			}
		}
		catch (Exception e)
		{
			try {
				destination.abortTransaction(txId);
			} catch (Exception e2) {
				// Unable to abort the transaction
				//ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ
				System.err.println("Unable to abort transaction");
			}
			if (e instanceof FipException)
				throw (FipException) e;
			FipException fipException = new FipException("Need to abort transaction: " + e.toString());
			fipException.setStackTrace(e.getStackTrace());
			throw fipException;
		}
	}

//	private void processCommit(String sourceUrl, String destinationUrl, String txId) throws FipException
//	{
//		FipServerProxy source = getServerProxy(sourceUrl);
//		FipServerProxy destination = getServerProxy(destinationUrl);
//		
//		// Verify the machines can talk:
//		// Get uuid from the source.
//		// Get uuid from the destination, and open a transaction (unless in listOnly mode).
//		String sourceUuid = source.askForUuid();
//		NewTransactionReply reply = destination.askForTransactionDetails(sourceUuid, txId);
//		String destinationUuid = reply.getDestinationUuid();
////		String source reply.getSourceUuid();
////		String txId = reply.gettxId();
//		String salt = reply.getSalt();
//
//		// Loop around, getting updates from the source and sending them to the destination, until there are none left
//		FipRequestList requestList = new FipRequestList();
//		long totalSent = 0;
//		boolean areChanges = false;
//		try {
//			requestList.addRequestForCommit();
//			processUpdatesAndDeletes(source, destination, requestList, destinationUuid, txId, salt, areChanges);
//		}
//		catch (Exception e)
//		{
//			// The commit probably failed.
//			if (e instanceof FipException)
//				throw (FipException) e;
//			FipException fipException = new FipException("The commit appears to have failed: " + e.toString());
//			fipException.setStackTrace(e.getStackTrace());
//			throw fipException;
//		}
//	}

//	private void processAbort(String destinationUrl)
//	{
//		
//	}

	private void processStatusCheck(String destinationUrl)
	{
		
	}

	private String sizeFmt(long size)
	{
		int kb = 1024;
		int mb = 1024 * 1024;
		
		if (size > 2 * mb)
		{
			size += (mb - 1); // Round up
			double num = ((double) size) / mb;
			DecimalFormat format = new DecimalFormat("######0.00");
			String str = format.format(num) + "mb";
			return str;
		}
		else
		{
			size += (kb - 1); // Round up
			int sizeKb = (int)(size / kb);
			String str = "" + sizeKb + "kb";
			return str;
		}
	}

	private FipServerProxy getServerProxy(String url) throws FipException
	{
		if (url.startsWith("http://"))
			url = url.substring(7);
		int pos = url.indexOf(":");
		if (pos >= 0)
		{
			// This is the url for a remote server (e.g. www.acme.com:40001/home/slot1/server)
			String host = url.substring(0, pos);
			String tmp = url.substring(pos + 1);
			pos = tmp.indexOf("/");
			if (pos < 0)
				throw new FipException("Invalid url: " + url);
			String portStr = tmp.substring(0, pos);
			int port = 0;
			try {
				port = Integer.parseInt(portStr);
			} catch (NumberFormatException e) { /* do nothing */ }
			if (port < 0 || port > 65535)
				throw new FipException("Invalid port number: " + url);
			String path = tmp.substring(pos);
			if ( !path.endsWith("/"))
				path += "/";
			if (path.length() < 5 || path.indexOf("/../")>=0)
				throw new FipException("Invalid port number: " + url);
			
			return new FipHttpServerProxy(host, port, path);
		}
		else
		{
			// This should be the path to directory on the current machine.
			File dir = new File(url);
			if ( !dir.exists() || !dir.isDirectory())
				throw new FipException("Unknown directory " + url);
			return new FipThisMachineServerProxy(url);
		}
	}

	/**
	 * The requests for any operation need to come from the source, because it places
	 * the seal on the buffer that confirms it's authenticity. So:
	 * 
	 * Step 1 - send the request list to the source
	 * Step 2 - send the updates returned by the source to the destination.
	 */
	private void processUpdatesAndDeletes(FipServerProxy source, FipServerProxy destination, FipRequestList requestList, String destinationUuid, String txId, String salt, boolean commitWillBeRequired) throws FipException, IOException, FipCorruptionException
	{
		// Get the actual updates from the source
		FipBatchOfUpdates updateInstructions;
		if (requestList.size() > 0)
			updateInstructions = source.askForUpdates(requestList, destinationUuid, txId, salt);
		else
			updateInstructions = new FipBatchOfUpdates();
		
		// Send this list of changes to the destination.
		if (commitWillBeRequired || requestList.size() > 0)
			destination.sendUpdates(txId, updateInstructions);
		
		// Reset the request list, and the download-buffer-size counter
		requestList.clear();
	}

//	public static byte[] unzipIt(byte[] compressed, String partName) throws FipException
//	{
//		ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(compressed));
//		try {
//	    	for ( ; ; )
//	    	{
//	    		ZipEntry entry = zip.getNextEntry();
//	    		if (entry == null)
//	    			break;
//	    		String filePath = entry.getName();
//	    		if ( !filePath.equals(partName))
//	    			continue;
//	    		
//	    		// Found the right entry, read it now.
//	    		byte[] buf = new byte[4096];
//	    		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
//	    		for ( ; ; )
//	    		{
//		    		int len = zip.read(buf);
//	    			if (len <= 0)
//	    				break;
//	    			byteArrayOutputStream.write(buf, 0, len);
//	    		}
//	    		return byteArrayOutputStream.toByteArray();
//	    	}
//	    	throw new FipException("Zipped content does not contain expected entry '" + partName + "'");
//		}
//		catch (IOException e) {
//	    	throw new FipException("Error unzipping: " + e.toString());
//		} finally {
//			try { zip.close(); } catch (Exception e) { /* can't do much about it */ }
//		}
//	}

	public static byte[] unzipIt(InputStream is, String partName) throws FipException
	{
    	ZipInputStream zip = new ZipInputStream(is);
    	try {
	    	for ( ; ; )
	    	{
	    		ZipEntry entry = zip.getNextEntry();
	    		if (entry == null)
	    			break;
	    		String filePath = entry.getName();
	    		if ( !filePath.equals(partName))
	    			continue;
	    		
	    		// Found the right entry, read it now.
	    		byte[] buf = new byte[4096];
	    		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
	    		for ( ; ; )
	    		{
		    		int len = zip.read(buf);
	    			if (len <= 0)
	    				break;
	    			byteArrayOutputStream.write(buf, 0, len);
	    		}
    		
	    		// Convert the buffer to a file list 
	    		return byteArrayOutputStream.toByteArray();
	    	}
	    	throw new FipException("Zipped content does not contain expected entry '" + partName + "'");
    	} catch (IOException e) {
	    	throw new FipException("Error unzipping: " + e.toString());
		} finally {
			try { zip.close(); } catch (Exception e) { /* can't do much about it */ }
		}

	}
	
	public static byte[] zipIt(byte[] byteArray, int length) throws IOException
	{
		// Zip this buffer
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		ZipOutputStream zipoutputstream = new ZipOutputStream(os);
		zipoutputstream.setMethod(ZipOutputStream.DEFLATED); // compressed
	
		// Add a single entry to the ZIP
		{
			// Calculate the CRC-32 value.  This isn't strictly necessary
			//   for deflated entries, but it doesn't hurt.
			CRC32 crc32 = new CRC32();
			crc32.update(byteArray, 0, length);
	
			// Create a zip entry.
			ZipEntry zipentry = new ZipEntry("data");
			zipentry.setSize(length);
			zipentry.setTime(System.currentTimeMillis());
			zipentry.setCrc(crc32.getValue());
	
			// Add the zip entry and associated data.
			zipoutputstream.putNextEntry(zipentry);
			zipoutputstream.write(byteArray, 0, length);
			zipoutputstream.closeEntry();
		}
	
		// Return the ZIP
		zipoutputstream.close();
		os.close();
		return os.toByteArray();
	}

	/**
	 * Prepare the index in a source or destination directory.
	 */
	public static void prepareIndex(String rootDirectory, boolean verbose) throws IOException, FipCorruptionException, FipException
	{
		// Get the file list
		File rootDir = new File(rootDirectory);
		if ( !rootDir.exists() || !rootDir.isDirectory())
		{
			System.err.println("Unknown source or destination folder: " + rootDirectory);
			System.exit(1);
		}
		
		System.out.println("Loading existing index...");
		long time1 = System.currentTimeMillis();
		FipList list = FipList.loadListFromFile(rootDir);
		long time2 = System.currentTimeMillis();
		long duration1 = time2 - time1;
		System.out.println(" - loaded " + list.numFiles() + " files in " + duration1 + "ms.");
		System.out.println("Updating index...");
		long time3 = System.currentTimeMillis();
		list.syncWithRealFiles(rootDir);
		long time4 = System.currentTimeMillis();
		long duration2 = time4 - time3;
		System.out.println(" - updated " + list.numFiles() + " files in " + duration2 + "ms.");
		
		long duration3 = duration1 + duration2;
		Runtime runtime = Runtime.getRuntime();
		long totalMemory = runtime.totalMemory() / 1024;
		long maxMemory = runtime.maxMemory() / 1024;
		System.out.println("Completed: " + list.numFiles()
				+ ", " + duration3 + ", " + duration1 + ", " + duration2
				+ "," + totalMemory + "/" + maxMemory);
	}

	public static void usage()
	{
		System.err.println("usage: fip [-l -v -p] source destination");
		System.err.println("       fip -s destination");
		System.err.println("       fip -c destination");
		System.err.println("       fip -a destination");
		System.err.println("       fip -i location");
		System.err.println("       fip -V");
		System.err.println("");
		System.err.println("  The 'source' and 'destination' locations can be either:");
		System.err.println("   - the path of a directory on the current machine, or");
		System.err.println("   - a URL similar to server1.acme.com:40001/home/slot1/server.");
		System.err.println("");
		System.err.println("Options:");
		System.err.println("  -l  List updates, but do not perform them on the destination.");
		System.err.println("  -v  Verbose mode. Display information useful for debugging problems.");
		System.err.println("  -s  Show the status, in particular, any previously incompleted transaction.");
		System.err.println("  -i  Prepare the index but do not copy files (only on local machine).");
		System.err.println("  -p  Use the index pre-prepared using the -i option.");
		System.err.println("  -c  Commit the current transaction");
		System.err.println("  -a  Abort the current transaction");
		System.err.println("  -V  Show the version number");
		System.exit(1);
	}
	
	public static void main(String[] args)
	{
		// Check that the current user is not root. This isn't foolproof, as the user.name property
		// can be spoofed by defining it on the command line. It does however prevent innocent errors.
		String currentUser = System.getProperty("user.name");
		if (currentUser != null && currentUser.trim().equals("root"))
		{
			System.err.println("Fatal Error: Fip commands may not be run as root.");
			System.err.println("Exiting now.");
			System.exit(1);
		}
		
		// Check the invocation parameters.
		boolean verbose = false;
		boolean listOnly = false;
		boolean statusOnly = false;
		boolean indexOnly = false;
		boolean preparedIndex = false; // Don't re-index
//		boolean abortFlag = false;
//		boolean commitFlag = false;
		boolean debugMessages = false;
		int numArgs = args.length;
		int cntarg = 0;
		int cntExcusiveArgs = 0;
		for ( ; cntarg < numArgs && args[cntarg].startsWith("-"); cntarg++)
		{
			String arg = args[cntarg];
			for (int j = 1; j < arg.length(); j++) {
                char flag = arg.charAt(j);
                switch (flag) {
                case 'v':
                    verbose = true;
                    break;
                case 'l':
                    listOnly = true;
                    break;
                case 'p':
                    preparedIndex = true;
                    break;
                case 's':
                    statusOnly = true;
                    break;
//                case 'c':
//                    commitFlag = true;
//                    cntExcusiveArgs++;
//                    break;
//                case 'a':
//                    abortFlag = true;
//                    cntExcusiveArgs++;
//                    break;
                case 'i':
                    indexOnly = true;
                    cntExcusiveArgs++;
                    break;
                case 'V':
   					System.out.println("FIP version " + MAJOR_VERSION_NUMBER + "." + MINOR_VERSION_NUMBER);
   					System.exit(0);
                    break;
                default:
                    usage();
                    break;
                }
			}
		}
		int numRemainingArgs = numArgs - cntarg;
		if (listOnly || preparedIndex) // implies normal install mode
			cntExcusiveArgs++;
		if (cntExcusiveArgs > 1)
			usage();
		
//		// Check that we only have one or none of status, and abort
//		if (statusOnly && abortFlag)
//			usage();

		try {
			Fip fip = new Fip();
			if (statusOnly)
			{
				// Show the destaination's status
				if (numRemainingArgs != 1)
					usage();
				String destinationUrl = args[cntarg + 1];
				fip.processStatusCheck(destinationUrl);
				System.exit(1);
			}
			else if (indexOnly)
			{
				if (numRemainingArgs != 1)
					usage();
				String localDirectory = args[cntarg];
				prepareIndex(localDirectory, verbose);
			}
//			else if (commitFlag)
//			{
//				// Commit changes to a destination
//				if (abortFlag)
//				{
//					System.err.println("Sorry, commit AND abort is not possible.");
//					System.exit(1);
//				}
//				if (numRemainingArgs != 3)
//					usage();
//				String sourceUrl = args[cntarg];
//				String destinationUrl = args[cntarg + 1];
//				String txId = args[cntarg + 2];
//				fip.processCommit(sourceUrl, destinationUrl, txId);
//				System.exit(1);
//			}
//			else if (abortFlag)
//			{
//				// Abort changes to a destination
//				if (numRemainingArgs != 1)
//					usage();
//				String destinationUrl = args[cntarg + 1];
//				fip.processAbort(destinationUrl);
//				System.exit(1);
//			}
			else
			{
				// Normal transfer (default is to commit after all files are transferred)
				if (numRemainingArgs != 2)
					usage();
				String sourceUrl = args[cntarg];
				String destinationUrl = args[cntarg + 1];
	//sourceUrl = "/Controller/launchpads/test_webdesign/image";
	//destinationUrl = "67.23.27.18:18990/home/tooltwist/server";
	//verbose = true;
	//listOnly = false;
				
					
				// Remote client and server
//				String srcRoot = "/tmp/fip_src";
//				String dstRoot = "/tmp/fip_dst";
				System.out.println("Installing from " + sourceUrl + " to " + destinationUrl);
					

//				fip.install(".*");
//				fip.ignore("private/.*");
//				fip.ignore("abc/.*");
	//fip.dontInstall("files/.*/config/wbd/wbd.conf");
	//fip.dontInstall("files/webdesign/config/wbd/.*");
	//fip.dontInstall("files/webdesign/config/userFavorites/.*");


	//fip.install("tomcat/.*");
	//fip.dontInstall("tomcat/logs/.*");
	//fip.dontInstall("tomcat/temp/.*");
	//fip.dontInstall("tomcat/webapps/.*");
	//fip.dontInstall("tomcat/work/.*");
	//fip.dontInstall("tomcat/conf/server.xml");
	//fip.dontInstall("tomcat/conf/tomcat-users.xml");
	//
	//String p_webappName = "ttsvr";
	//
	//fip.install("tomcat/webapps/ttsvr/.*");
	//fip.dontInstall("tomcat/webapps/ttsvr/WEB-INF/web.xml");
	//fip.dontInstall("tomcat/webapps/ttsvr/thumbnails/.*");
	////fip.rule(new FipRule_renameWebapp("tomcat/webapps/ttsvr/.*", p_webappName));
	//
	//
	//fip.map("production/web.xml", "tomcat/webapps/ttsvr/web.xml");
	//fip.map("production/server.xml", "tomcat/conf/server.xml");
	//fip.map("production/tomcat-users.xml", "tomcat/conf/tomcat-users.xml");
	//fip.map("production/tooltwist.conf", "tooltwist.conf");
	//fip.map("production/wbd.conf", "files/webdesign/config/wbd/wbd.conf");

				fip.installFiles(sourceUrl, destinationUrl, debugMessages, verbose, listOnly);
				System.out.println("Finished");
			}

			
			
		} catch (Exception e) {
			System.err.println("Error: " + e.toString());
			e.printStackTrace();
		}
	}

}
