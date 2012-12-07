package tooltwist.fip;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;

import org.apache.log4j.Logger;

import tooltwist.fip.DestinationProperties.CommitMode;


/*
 * Roll Forward operations.
 * 	New file:
 * 		create !+ttfip_txId:file
 * 		+ttfip_txId:file  => file
 * 
 * 	Change file:
 * 		file => !@ttfip_txId:file
 * 		@ttfip_txId:file  => file
 * 
 * 	Delete file:
 * 		file => !-ttfip_txId:file
 * 		remove -ttfip_txId:file
 * 
 * Roll back operations:
 * 
 * 	Was a new file:
 * 		file => +ttfip_txId:file
 * 		remove !+ttfip_txId:file
 * 
 * 	Was a changed file:
 * 		file => @ttfip_txId:file
 * 		!@ttfip_txId:file => file
 * 
 * 	Was a delete file:
 * 		create -ttfip_txId:file
 * 		!-ttfip_txId:file => file
 * 		
 */
/**
 * This class unpacks a buffer created using FipUpdateInstructionsBuffer. It performs
 * the reverse of the marshalling performed by class {@link FipBatchOfUpdates}.
 */
public class FipServer_updateExecuter
{
	private static Logger logger = Logger.getLogger(FipServer_updateExecuter.class);
	
	public static final String UNDERSTUDY_PREFIX_NEW_FILE = "+fip_";
	public static final String UNDERSTUDY_PREFIX_CHANGE_FILE = "@fip_";
	public static final String UNDERSTUDY_PREFIX_DELETE_FILE = "-fip_";
	public static final String ROLLBACK_FILE_PREFIX = "!";
	public static final String SEPARATOR_FOR_AFTER_txId = ":";
	public static final String FIP_FILE_PREFIX = ".fip-";
	private byte[] buf;
	private int length;
	private int currentPos;

//	public FipUpdateExecuter(byte[] buf, int len)
//	{
////logger.info("Received buffer " + len + " long");
//		this.buf = buf;
//		this.length = len;
//		this.currentPos = 0;
//	}

	public FipServer_updateExecuter(String destinationRoot, FipBatchOfUpdates updates, String txId, String salt, String passphrase, String ipaddr) throws FipException
	{
		this.buf = updates.getBuffer();
		this.length = updates.getLength();
		this.currentPos = 0;

		// Take the seal (the hash value) off the end of the buffer, and check that it is correct.
		if (length < FipBatchOfUpdates.SEAL_LENGTH)
			throw new FipException("Internal error: buffer is shorter than the seal/hash that should be placed on the end.");
		length -= FipBatchOfUpdates.SEAL_LENGTH;

		// Calculate the hash value, based upon the data, the txId, the salt, and the passphrase.
		byte digest[];
		try {
			MessageDigest m = MessageDigest.getInstance("SHA-1");
			m.update(buf, 0, length);
			m.update(txId.getBytes("iso-8859-1"));
			m.update(salt.getBytes("iso-8859-1"));
			m.update(passphrase.getBytes("iso-8859-1"));
			digest = m.digest();
		} catch (Exception e) {
			throw new FipException("Could not calculate seal: " + e.toString());
		}
		
		// Check the digest looks correct
		if (digest.length != FipBatchOfUpdates.SEAL_LENGTH)
			throw new FipException("Seal hash value is not the expected length");

		// For debugging only
		BigInteger bigInt = new BigInteger(1,digest);
		String hashtext = bigInt.toString(16);
		while (hashtext.length() < 40)
		  hashtext = "0"+hashtext;
//		logger.info("hash is " + hashtext);
		
		// Compare the calculated seal with the one on the end of the buffer
		for (int i = 0; i < FipBatchOfUpdates.SEAL_LENGTH; i++)
			if (digest[i] != buf[length + i])
			{
				FipServer.log(destinationRoot, true, "Incorrect seal on buffer. Potential hack attempt from " + ipaddr);
				throw new FipException("Incorrect seal on buffer. Potential hack attempt from " + ipaddr);
			}

		// It looks like everything is okay.
	}

	public FipInstallUpdatesStatus executeUpdates(String destinationRoot, DestinationProperties destinationProperties, String txId) throws FipException
	{
		expect(FipBatchOfUpdates.MAGIC_START_OF_TRANSFER_FILE, "Batch file corrupt");
		byte major = getByte();
		byte minor = getByte();
		

		if (major == 0 && minor == 1)
		{
			// Not supported any more (initial insecure test version)
		}
		
		// Put old formats that are still supported in here
		
		
//		else if (major == 1 && minor == 0)
//		{
//			return prepareUpdates_0_1(destinationRoot, destinationProperties, txId);
//		}
//		else if (major == 1 && minor == 2)
//		{
//			return prepareUpdates_0_1(destinationRoot, destinationProperties, txId);
//		}
		
		// Increase the buffer size. All old formats are obsolete
		else if (major == Fip.MAJOR_VERSION_NUMBER && minor == Fip.MINOR_VERSION_NUMBER)
		{
			return prepareUpdates_1_3(destinationRoot, destinationProperties, txId);
		}
		
		throw new FipException("Unknown protocol version: " + major + "." + minor);
	}

	private FipInstallUpdatesStatus prepareUpdates_1_3(String destinationRoot, DestinationProperties destinationProperties, String txId) throws FipException
	{
		try {

			for ( ; ; )
			{
				if (this.currentPos >= this.length)
				{
					// ZZZZZ Abort the transaction
					throw new FipException("Ran off end of buffer");
				}
				byte type = getByte();
				switch (type)
				{
				case FipBatchOfUpdates.OP_INSTALL_FILE:
					{
						// Path
						String relativePath = getString();
						// Get the flags
						byte flags = getByte();
						boolean canExecute = (flags & FipBatchOfUpdates.FLAG_EXECUTABLE) != 0x0;
						// File length
						int fileLength = getFileLength();
						// File contents
						expect(FipBatchOfUpdates.MAGIC_BEFORE_FILE_CONTENTS, "MAGIC_START_FILE missing");
						int positionOfContents = currentPos;
						currentPos += fileLength;
						expect(FipBatchOfUpdates.MAGIC_AFTER_FILE_CONTENTS, "MAGIC_END_FILE missing");
	//logger.info("=> install " + relativePath + ", " + fileLength + "");
						
						if (destinationProperties.isProtected(relativePath))
						{
							FipServer.log(destinationRoot, false, "  Ignoring I " + relativePath + " (protected file)");
						}
						else
						{
							// Write to the log file
							FipServer.log(destinationRoot, false, "  I " + relativePath);
							
							// Create the understudy file
							prepareInstallUnderstudyFile(destinationRoot, txId, relativePath, positionOfContents, fileLength, canExecute);
						}
					}
					break;
					
				case FipBatchOfUpdates.OP_DELETE_FILE:
					{
						String relativePath = getString();
	//logger.info("=> delete " + relativePath);
						expect(FipBatchOfUpdates.MAGIC_AFTER_DELETE, "MAGIC_AFTER_DELETE missing");
						
						if (destinationProperties.isProtected(relativePath))
						{
							FipServer.log(destinationRoot, false, "  Ignoring D " + relativePath + " (protected file)");
						}
						else
						{
							// Write to the log file
							FipServer.log(destinationRoot, false, "  D " + relativePath);
							
							// Create the understudy file
							prepareDeleteUnderstudyFile(destinationRoot, txId, relativePath);
						}
					}
					break;

				case FipBatchOfUpdates.MAGIC_END_OF_BATCH:
					{
//						// Change the transaction status
//						TransactionProperties txProperties = TransactionProperties.loadTransactionProperties(destinationRoot, txId);
//						txProperties.changeStatus(destinationRoot, TransactionStatus.READY_TO_COMMIT);

						// Write to the log file
						FipServer.log(destinationRoot, false, "  End of batch");

		//				logger.info("End of transfer file");
						if (destinationProperties.getCommitMode() == CommitMode.COMMIT_AFTER_EVERY_FILE)
						{
							logger.info("Committing files in this transfer");
							long start = System.currentTimeMillis();
							commitTransaction(destinationRoot, destinationProperties, txId);
							long end = System.currentTimeMillis();
							long duration = end - start;
							logger.info("Commit completed in " + duration + "ms.");
						}
						
						return FipInstallUpdatesStatus.OK;
					}

				case FipBatchOfUpdates.OP_END_OF_TRANSACTION:
					{
						// Write to the log file
						FipServer.log(destinationRoot, false, "  End of transaction");

						// Change the transaction status
						if (destinationProperties.getCommitMode() == CommitMode.COMMIT_AS_A_TRANSACTION)
						{
							TransactionProperties txProperties = TransactionProperties.loadTransactionProperties(destinationRoot, txId);
							txProperties.changeStatus(destinationRoot, TransactionStatus.READY_TO_COMMIT);
	
							logger.info("Committing transaction");
							long start = System.currentTimeMillis();
							commitTransaction(destinationRoot, destinationProperties, txId);
							long end = System.currentTimeMillis();
							long duration = end - start;
							logger.info("Commit completed in " + duration + "ms.");
						}
						return FipInstallUpdatesStatus.COMMITTED;
					}
				case FipBatchOfUpdates.OP_COMMIT_TRANSACTION:
					{
						// Write to the log file
						FipServer.log(destinationRoot, false, "  Manual commit");

						logger.info("Manually committing");
						long start = System.currentTimeMillis();
						commitTransaction(destinationRoot, destinationProperties, txId);
						long end = System.currentTimeMillis();
						long duration = end - start;
						logger.info("Commit completed in " + duration + "ms.");
						return FipInstallUpdatesStatus.COMMITTED;
					}
					
				case FipBatchOfUpdates.OP_ABORT_TRANSACTION:
					{
						// Write to the log file
						FipServer.log(destinationRoot, false, "  Abort transaction");

						logger.info("Aborting transaction");
						long start = System.currentTimeMillis();
						abortTransaction(destinationRoot, txId);
						long end = System.currentTimeMillis();
						long duration = end - start;
						logger.info("Abort completed in " + duration + "ms.");
						return FipInstallUpdatesStatus.ABORTED;
					}
					
				default:
					// Write to the log file
					FipServer.log(destinationRoot, false, "ERROR: Unknown operation in batch file");
					throw new FipException("Unknown operation in batch file");
				}
			}
		} catch (IOException e) {
			FipException fipException = new FipException("Error processing updates: " + e.toString());
			fipException.setStackTrace(e.getStackTrace());
			throw fipException;
		}
		
	}

	protected static void commitTransaction(String destinationRoot, DestinationProperties destinationProperties, String txId) throws IOException, FipException
	{

		// Run the pre-committing operation (e.g. stop the web server)
		String preCommitCommand = destinationProperties.getPreCommitCommand();
		if (preCommitCommand!=null && !preCommitCommand.equals(""))
		{
			FipServer.log(destinationRoot, false, "  Pre-commit command: " + preCommitCommand);
			long start = System.currentTimeMillis();
			if ( !runCommand("preCommit", destinationRoot, preCommitCommand))
				throw new FipException("Pre-commit command failed: " + preCommitCommand);
			long end = System.currentTimeMillis();
			long duration = end - start;
			FipServer.log(destinationRoot, false, "  Pre-commit completed in " + duration + "ms.");
		}
		
		// Check the directories
		String transactionRoot = TransactionProperties.transactionDirectory(destinationRoot, txId, TransactionStatus.READY_TO_COMMIT);
		File dir = new File(transactionRoot);
		if ( !dir.isDirectory())
			throw new FipException("Cannot find transaction directory: " + transactionRoot);
		String transactionUndoRoot = TransactionProperties.transactionDirectory(destinationRoot, txId, TransactionStatus.ROLLBACK);
		
		// Do the commit
//		FipServer.log(destinationRoot, false, "  Committing");
		long start = System.currentTimeMillis();
		commit_recursive(transactionRoot, transactionUndoRoot, destinationRoot, dir, txId);
		long end = System.currentTimeMillis();
		long duration = end - start;
		FipServer.log(destinationRoot, false, "  Commit completed in " + duration + "ms.");

		// Run the post-commit operation (e.g. start the web server again)
		String postCommitCommand = destinationProperties.getPostCommitCommand();
		if (postCommitCommand!=null && !postCommitCommand.equals(""))
		{
			FipServer.log(destinationRoot, false, "  Post-commit command: " + postCommitCommand);
			start = System.currentTimeMillis();
			runCommand("postCommit", destinationRoot, postCommitCommand);
			end = System.currentTimeMillis();
			duration = end - start;
			FipServer.log(destinationRoot, false, "  Post-commit completed in " + duration + "ms.");
		}
		
		// Move the transaction properties file to the new directory
		File oldPropertiesFile = new File(transactionRoot + "/" + TransactionProperties.TRANSACTION_PROPERTIES_FILE);
		File newPropertiesFile = new File(transactionUndoRoot + "/" + TransactionProperties.TRANSACTION_PROPERTIES_FILE);
		oldPropertiesFile.renameTo(newPropertiesFile);
		
		// Remove the old batch directory
		recursivelyDeleteEmptyDirectoryHierarchy(dir);
	}

	/**
	 * Delete a hierarchy of directories that contains no real files. Throw an
	 * exception if any real file is found.
	 * 
	 * @param dir
	 * @throws FipException 
	 */
	private static void recursivelyDeleteEmptyDirectoryHierarchy(File dir) throws FipException
	{
		// Delete any children first
		for (File f : dir.listFiles())
		{
			if ( !f.isDirectory())
				throw new FipException("Expected the prepare directory to be empty");
			recursivelyDeleteEmptyDirectoryHierarchy(f);
		}
		
		// Delete the directory now.
		if ( !dir.delete())
			throw new FipException("Could not delete directory: " + dir.getAbsolutePath());
	}

	private static void commit_recursive(String transactionRoot, String transactionUndoRoot, String destinationRoot, File dir, String txId) throws IOException, FipException
	{
		String prefix_new = UNDERSTUDY_PREFIX_NEW_FILE;
		String prefix_change = UNDERSTUDY_PREFIX_CHANGE_FILE;
		String prefix_delete = UNDERSTUDY_PREFIX_DELETE_FILE;
		String rollback_new = ROLLBACK_FILE_PREFIX + UNDERSTUDY_PREFIX_NEW_FILE;
		String rollback_change = ROLLBACK_FILE_PREFIX + UNDERSTUDY_PREFIX_CHANGE_FILE;
		String rollback_delete = ROLLBACK_FILE_PREFIX + UNDERSTUDY_PREFIX_DELETE_FILE;
//if (1==1)
//throw new FipException("Fake abort");		
		
		
		// Get the equivalent path under the destination directory, rather than under the transaction directory.
		String absoluteTransactionPath = dir.getAbsolutePath();
		if ( !absoluteTransactionPath.startsWith(transactionRoot))
			throw new FipException("Internal error: path should start with transaction directory path: " + absoluteTransactionPath);
		String relativePath = absoluteTransactionPath.substring(transactionRoot.length());
		String absoluteUndoPath = transactionUndoRoot + relativePath;
		File undoDir = new File(absoluteUndoPath);
		if ( !undoDir.exists())
			undoDir.mkdir();
		String absoluteDestinationPath = destinationRoot + relativePath;
		File destinationDir = new File(absoluteDestinationPath);
		if ( !destinationDir.exists())
			destinationDir.mkdir();
		
		// Look for understudy files.
		for (File f : dir.listFiles())
		{
			if (f.isDirectory())
				commit_recursive(transactionRoot, transactionUndoRoot, destinationRoot, f, txId);
			else if (f.isFile())
			{
				String name = f.getName();
				if (name.startsWith(prefix_new))
				{
					/*
					 * 	New file:
					 * 		create !+ttfip_txId:file
					 * 		+ttfip_txId:file  => file
					 */
					String realFilename = name.substring(prefix_new.length());
					File rollbackFile = new File(undoDir.getAbsolutePath() + File.separator + rollback_new + realFilename);
					File realFile = new File(destinationDir.getAbsolutePath() + File.separator + realFilename);

					// Create an empty rollback file
					rollbackFile.createNewFile();
					
					// Move the understudy file to the real location
					f.renameTo(realFile);
				}
				else if (name.startsWith(prefix_change))
				{
					/*
					 * 	Change file:
					 * 		file => !@ttfip_txId:file
					 * 		@ttfip_txId:file  => file
					 */
					String realFilename = name.substring(prefix_new.length());
					File realFile = new File(destinationDir.getAbsolutePath() + File.separator + realFilename);
					File rollbackFile = new File(undoDir.getAbsolutePath() + File.separator + rollback_change + realFilename);

					// Move the existing file to be the rollback file.
					realFile.renameTo(rollbackFile);
					
					// Move the understudy file to the real location
					f.renameTo(realFile);
				}
				else if (name.startsWith(prefix_delete))
				{
					/*
					 * Roll Forward operations.
					 * 	Delete file:
					 * 		file => !-ttfip_txId:file
					 * 		remove -ttfip_txId:file
					 */
					// Create a rollback file, named !+ttfip_txId:file
					String realFilename = name.substring(prefix_new.length());
					File rollbackFile = new File(undoDir.getAbsolutePath() + File.separator + rollback_delete + realFilename);
					File realFile = new File(destinationDir.getAbsolutePath() + File.separator + realFilename);

					// Move the existing file to be the rollback file
					realFile.renameTo(rollbackFile);
					
					// Remove the understudy file (which is only a marker file)
					f.delete();
				}
			}
		}
	}

	/**
	 * The transaction is being aborted, so remove all the understudy files.
	 * @param rootPath
	 * @param txId
	 * @throws FipException 
	 */
	protected static void abortTransaction(String destinationRoot, String txId) throws FipException
	{
//		File dir = new File(destinationRoot);
//		removeUnderStudyFiles_recursive(dir, txId);
		TransactionProperties txProperties = TransactionProperties.loadTransactionProperties(destinationRoot, txId);
		txProperties.changeStatus(destinationRoot, TransactionStatus.ABORTED);
	}

//	private static void removeUnderStudyFiles_recursive(File dir, String txId)
//	{
//		String p1 = UNDERSTUDY_PREFIX_NEW_FILE;
//		String p2 = UNDERSTUDY_PREFIX_CHANGE_FILE;
//		String p3 = UNDERSTUDY_PREFIX_DELETE_FILE;
//		String p4 = ROLLBACK_FILE_PREFIX + UNDERSTUDY_PREFIX_NEW_FILE;
//		String p5 = ROLLBACK_FILE_PREFIX + UNDERSTUDY_PREFIX_CHANGE_FILE;
//		String p6 = ROLLBACK_FILE_PREFIX + UNDERSTUDY_PREFIX_DELETE_FILE;
//
//		for (File f : dir.listFiles())
//		{
//			if (f.isDirectory())
//				removeUnderStudyFiles_recursive(f, txId);
//			else if (f.isFile())
//			{
//				String name = f.getName();
//				if (name.startsWith(p1) || name.startsWith(p2) || name.startsWith(p3) || name.startsWith(p4) || name.startsWith(p5) || name.startsWith(p6))
//					f.delete();
//			}
//		}
//	}

	private void prepareInstallUnderstudyFile(String destinationRoot, String txId, String relativePath, int positionOfContents, int length, boolean canExecute) throws IOException
	{
		// Separate the path in the directory and the file name.
		String transactionDirectory = TransactionProperties.transactionDirectory(destinationRoot, txId, TransactionStatus.PREPARING);
		String path = transactionDirectory + File.separator + relativePath;
		File file = new File(path);
		boolean isNew = !file.exists();
		File dirfile = file.getParentFile();
		String dir = dirfile.getAbsolutePath();
		String filename = file.getName();
		
		// Check the directory exists
		if ( !dirfile.exists())
		{
//			logger.info(" creating directory " + dir);
			dirfile.mkdirs();
		}

		// Work out a new "pending commit" filename.
		String newFilename = (isNew ? UNDERSTUDY_PREFIX_NEW_FILE : UNDERSTUDY_PREFIX_CHANGE_FILE) + filename;
		String newPath = dir + File.separator + newFilename;
		
		// Save the file
		FileOutputStream fis = null;
		try {
			fis = new FileOutputStream(newPath);
			fis.write(this.buf, positionOfContents, length);
		} finally {
			if (fis != null)
				try { fis.close(); } catch (IOException e) { }
		}
		
		// Set the file mode
		if (canExecute)
		{
			File newFile = new File(newPath);
			newFile.setExecutable(true);
		}
//		logger.info(" creating " + newPath);
	}

	public static void prepareDeleteUnderstudyFile(String destinationRoot, String txId, String relativePath) throws IOException
	{
		// Separate the path in the directory and the file name.
		String transactionDirectory = TransactionProperties.transactionDirectory(destinationRoot, txId, TransactionStatus.PREPARING);
		String path = transactionDirectory + File.separator + relativePath;
		File file = new File(path);
		File dirfile = file.getParentFile();
		String dir = dirfile.getAbsolutePath();
		String filename = file.getName();
		
		// Check the directory exists
		if ( !dirfile.exists())
		{
//			logger.info(" creating directory " + dir);
			dirfile.mkdirs();
		}

		// Work out a new "pending commit" filename.
		String newFilename = UNDERSTUDY_PREFIX_DELETE_FILE + filename;
		String newPath = dir + File.separator + newFilename;
		
		// Create the shadow file
		File newFile = new File(newPath);
		newFile.createNewFile();
		
//		logger.info(" creating " + newPath);
	}

	private int getFileLength()
	{
		byte b4 = buf[currentPos++];
		byte b3 = buf[currentPos++];
		byte b2 = buf[currentPos++];
		byte b1 = buf[currentPos++];

		// bytes are signed, so convert them to non-signed before we do arithmentic
		short s4 = (short) (0xff & (short) b4);
		short s3 = (short) (0xff & (short) b3);
		short s2 = (short) (0xff & (short) b2);
		short s1 = (short) (0xff & (short) b1);
		
		int len = s4;
		len = (len * 256) + s3;
		len = (len * 256) + s2;
		len = (len * 256) + s1;
		return len;
	}

	private byte getByte()
	{
		return buf[currentPos++];
	}

	private String getString() throws FipException
	{
		expect(FipBatchOfUpdates.MAGIC_BEFORE_STRING, "Invalid String");
		byte b2 = buf[currentPos++];
		byte b1 = buf[currentPos++];

		// bytes are signed, so convert them to non-signed before we do arithmentic
		short s2 = (short) (0xff & (short) b2);
		short s1 = (short) (0xff & (short) b1);

		int len = (s2 * 256) + s1;
		
		String string = new String(buf, currentPos, len);
		currentPos += len;
		expect(FipBatchOfUpdates.MAGIC_AFTER_STRING, "Invalid String");
		return string;
	}

	private void expect(byte expectedByte, String errMsg) throws FipException
	{
		if (buf[currentPos] != expectedByte)
			throw new FipException(errMsg);
		currentPos++;
	}
	/**
	 * Tries to exec the command, waits for it to finish, logs errors if exit
	 * status is nonzero, and returns true if exit status is 0 (success).
	 * 
	 * @param command
	 *            Description of the Parameter
	 * @return Description of the Return Value
	 * @throws WbdException 
	 */
	private static boolean runCommand(String threadName, String directory, String command) throws FipException
	{
		// See if this is a windows machine
//		String iwStr = WbdCache.getProperty("isWindows");
//		boolean isWindows = (iwStr != null && iwStr.toUpperCase().equals("Y"));
		boolean isWindows = false;
		String errorFile;
		if (isWindows)
		{
			/*
			 * Windows machine
			 */
//			// Use windows CMD to invoke the convert command
//			params.insertElementAt("cmd", 0);
//			params.insertElementAt("/c", 1);
//			params.insertElementAt("convert", 2);
			
			errorFile = "c:\\fipServerError.txt";
		}
		else
		{
			/*
			 * Non-windows (OSX, UNIX, LINUX)
			 */
			errorFile = "/tmp/fipServerError.txt";
		}
		
//		String[] command = (String[]) params.toArray(new String[params.size()]);
		// Nice debug message
//		if (1 == 1)
//		{
			logger.debug("Running: " + command);
//		}

		// Start the command
		Process proc;
		try
		{
			File dir = new File(directory);
			proc = Runtime.getRuntime().exec(command, null, dir);
		}
		catch (IOException e)
		{
			logger.error("IOException while trying to execute " + command + ": " + e);
			return false;
		}

		// logger.debug("Got process object, waiting to return.");
		// any error message?

		// Redirect output from the process
		try
		{
			FileOutputStream fos = new FileOutputStream(errorFile);
			StreamGobbler errorGobbler = new StreamGobbler(threadName, proc.getErrorStream(), "ERROR", fos);

			// any output?
			StreamGobbler outputGobbler = new StreamGobbler(threadName, proc.getInputStream(), "OUTPUT", null);

			// kick them off
			errorGobbler.start();
			outputGobbler.start();
		}
		catch (IOException e)
		{
			logger.error("IOException while trying to execute " + command);
		}

		int exitStatus;

		while (true)
		{
			try
			{
				exitStatus = proc.waitFor();
				break;
			}
			catch (java.lang.InterruptedException e)
			{
				logger.error("Interrupted: Ignoring and waiting");
			}
		}
		if (exitStatus != 0)
		{
			logger.error("Error status: " + exitStatus);
		}
		return (exitStatus == 0);
	}
	
	public String toString()
	{
		return "Rcv buffer, position " + currentPos + " in " + length + " byte buffer.";
	}
}
