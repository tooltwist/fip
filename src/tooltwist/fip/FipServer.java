package tooltwist.fip;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.AlgorithmParameters;
import java.util.Date;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.apache.log4j.helpers.RelativeTimeDateFormat;

import tooltwist.fip.FipRequest.RequestType;

/**
 * Irrespective of whether a source or destination is on the local machine or on a
 * remote machine access via a server, this is the class that does the actual work.
 * 
 * For a local endpoint, this class is called via {@link FipThisMachineProxy}.
 * For a remote endpoint, it is called via {@link FipHttpServerProxy}.
 * 
 * @author philipcallender
 *
 */
public class FipServer
{
	/**
	 * DESTINATION: Start a new transaction.
	 *
	 * @param sourceUuid
	 * @param destinationRoot
	 * @param ipaddr
	 * 
	 * @return
	 * @throws FipException
	 */
	public NewTransactionReply destination_startNewTransaction(String sourceUuid, String destinationRoot, String ipaddr) throws FipException
	{
		log(destinationRoot, true, "startNewTransaction (sourceUuid: "+sourceUuid + ", ipaddr: " + ipaddr + ")");

		// Load the destination properties
		DestinationProperties destinationProperties = new DestinationProperties(destinationRoot, ipaddr);
		
		// Check that the source Uuid is the correct uuid
		if ( !sourceUuid.equals(destinationProperties.getSourceUuid()))
		{
			log(destinationRoot, true, "startNewTransaction: Incorrect source Uuid " + "(called from "+ipaddr+")");
			throw new FipException("Incorrect source Uuid " + "(called from "+ipaddr+")");
		}
		
		// Create a new transaction id
		long now = System.currentTimeMillis();
		String txId = "" + now;
		
		// Create the transaction directory.
		String dirPath = TransactionProperties.transactionDirectory(destinationRoot, txId, TransactionStatus.PREPARING);
		File dir = new File(dirPath);
		dir.mkdir();

		// Generate a random number for the salt
		long aHardToPredictNumber = dir.getFreeSpace();
		long tmp = now + aHardToPredictNumber;
		Random random = new Random(tmp);
		String salt = "" + random.nextLong();
		
		// Decide how long this transaction has to complete
		long expires = now + (1000 * 60 * 60 * 4); // Two hours 

		// Create a transaction properties file
		TransactionProperties.createPropertiesFile(destinationRoot, txId, TransactionStatus.PREPARING, salt, expires, ipaddr);

		// Write to the log file
		String expiryDate = new Date(expires).toString();
		String logMsg = "Started new batch (ipaddr="+ipaddr+", expires="+expiryDate+").";
		log(destinationRoot, true, logMsg);


//		String txId = prop.getProperty("txId");
//		if (txId == null || txId.equals(""))
//			throw new FipException("Error: txId is not defined in " + path);
//		String salt = prop.getProperty("salt");
//		if (salt == null || salt.equals(""))
//			throw new FipException("Error: uuid is not defined in " + path);
		String destinationUuid = destinationProperties.getDestinationUuid();
		return new NewTransactionReply(destinationUuid, txId, salt);
	}

	public NewTransactionReply destination_askForTransactionDetails(String sourceUuid, String destinationRoot, String txId, String ipaddr) throws FipException
	{
		// Load the destination properties
		DestinationProperties destinationProperties = new DestinationProperties(destinationRoot, ipaddr);
		String destinationUuid = destinationProperties.getDestinationUuid();
		
		// Check that the source Uuid is the correct uuid
		if ( !sourceUuid.equals(destinationProperties.getSourceUuid()))
		{
			log(destinationRoot, true, "Incorrect source Uuid " + "(called from "+ipaddr+")");
			throw new FipException("Incorrect source Uuid " + "(called from "+ipaddr+")");
		}

		// Find any uncommitted transaction
		TransactionProperties txProperties = TransactionProperties.loadTransactionProperties(destinationRoot, txId);
		String salt = txProperties.getSalt();

		return new NewTransactionReply(destinationUuid, txId, salt);
	}

	/**
	 * SOURCE and DESTINATION: get the list of files at a location.
	 * 
	 * @param rootDirectory
	 * @return
	 * @throws IOException
	 * @throws FipCorruptionException
	 * @throws FipException
	 */
	public FipList getFileList(String rootDirectory, boolean isDestination, String ipaddr) throws IOException, FipCorruptionException, FipException
	{
		if (isDestination)
		{
			// Check the destination properties are okay
			new DestinationProperties(rootDirectory, ipaddr); // will throw an exception if there is a problem.
		}
		else
		{
			// Check the source properties are okay
			new SourceProperties(rootDirectory, ipaddr); // will throw an exception if there is a problem.
		}

		// Get the file list
		File rootDir = new File(rootDirectory);
		FipList list = FipList.loadListFromFile(rootDir);
		list.syncWithRealFiles(rootDir);
		return list;
	}

	/**
	 * DESTINATION: Prepare the updates. They will be committed in a separate request.
	 * 
	 * Receive a batch transmission. This is a bunch of updates (new/change/delete), which may or may not
	 * be an entire transaction. For each update, an understudy file is created. If a commit is received,
	 * these are used to install the updates. If an abort is received, these files are removed.
	 * 
	 * @param destinationRoot
	 * @param txId
	 * @param data
	 * @param len
	 * @param updateBuffer 
	 * @return Salt for next transmission
	 * @throws IOException
	 * @throws FipCorruptionException
	 * @throws FipException
	 */
	public void destination_installBatchOfFiles(String destinationRoot, String txId, FipBatchOfUpdates updateBuffer, String ipaddr) throws FipException
	{
		log(destinationRoot, true, "installBatchOfFiles (txId: " + txId + ", ipaddr: " + ipaddr+")");

		// Load the destination properties
//		if ( !destinationRoot.endsWith("/"))
//			destinationRoot += "/";
		DestinationProperties destinationProperties = new DestinationProperties(destinationRoot, ipaddr);
		String passphrase = destinationProperties.getPassphrase();
		
		// Check the properties of the transaction
		TransactionProperties txProperties = TransactionProperties.loadTransactionProperties(destinationRoot, txId);
		TransactionStatus status = txProperties.getStatus();
		if (status != TransactionStatus.PREPARING)
		{
			if (status == TransactionStatus.EXPIRED)
			{
				log(destinationRoot, true, "installBatchOfFiles: transaction has expired.");
				throw new FipException("Cannot transfer files: transaction has expired.");
			}
			log(destinationRoot, true, "installBatchOfFiles: Transaction has incorrect status: " + status.getDescription());
			throw new FipException("Transaction has incorrect status: " + status.getDescription());
		}
		String txSalt = txProperties.getSalt();
		long txExpiry = txProperties.getExpires();
		String txIpaddr = txProperties.getIpaddr();

		// Check this batch is okay
		if ( !txIpaddr.equals(ipaddr))
		{
			log(destinationRoot, true, "installBatchOfFiles: batch of updates received from incorrect IP address '"+ipaddr+"'. May be a hacking attempt.");
			throw new FipException("Error: batch of updates received from incorrect IP address '"+ipaddr+"'. May be a hacking attempt.");
		}
		long now = System.currentTimeMillis();
		if (now > txExpiry)
		{
			log(destinationRoot, true, "installBatchOfFiles: transaction timeout.");
			throw new FipException("Error: transaction timeout.");
		}
		
		// Looks okay, let's process the updates
		FipServer_updateExecuter helper = new FipServer_updateExecuter(destinationRoot, updateBuffer, txId, txSalt, passphrase, ipaddr);
		helper.executeUpdates(destinationRoot, destinationProperties, txId);
	}

	/**
	 * SOURCE: create a buffer containing requested updates and deletes.
	 */
	public FipBatchOfUpdates source_getRequestedUpdates(String sourceRoot, FipRequestList requestList, String destinationUuid, String txId, String salt, String ipaddr) throws IOException, FipException, FipCorruptionException
	{
		log(sourceRoot, true, "getRequestUpdates (destination: "+destinationUuid+", txId: "+txId+", paddr: "+ipaddr+")");
		SourceProperties sourceProperties = new SourceProperties(sourceRoot, ipaddr);
		
		// Load the manifest first. We only want to provide files in this list. Anyone asking for different files is potentially snooping.
		FipList fileList = getFileList(sourceRoot, false, ipaddr);

		// Read the updates
		FipBatchOfUpdates updateList = new FipBatchOfUpdates();
		for (FipRequest request : requestList.list())
		{
			RequestType type = request.getType();
			if (type == RequestType.DELETE)
			{
				// Delete request
				String destinationRelativePath = request.getDestinationRelativePath();
				
				// Check there is nothing dangerous in the path
				if (destinationRelativePath.startsWith("../") || destinationRelativePath.indexOf("/../") >= 0)
				{
					log(sourceRoot, true, "WARNING: POTENTIAL HACKING ATTEMPT! Path of file to delete contains '/../': " + destinationRelativePath + " (ipaddr="+ipaddr+")");
					throw new FipException("WARNING: POTENTIAL HACKING ATTEMPT! Path of file to delete contains '/../': " + destinationRelativePath + " (ipaddr="+ipaddr+")");
				}
				
				// Add it to the buffer
				updateList.addDeleteToBuffer(destinationRelativePath);
				log(sourceRoot, false, "  - "+destinationRelativePath);
			}
			else if (type == RequestType.UPDATE)
			{
				// Update request.
				String sourceRelativePath = request.getSourceRelativePath();
				
				// Check it is in the manifest file.
				if ( !fileList.containsFile(sourceRelativePath))
				{
					log(sourceRoot, true, "WARNING: POTENTIAL HACKING ATTEMPT! Asking for file not in file list: " + sourceRelativePath + " (ipaddr="+ipaddr+")");
					throw new FipException("WARNING: POTENTIAL HACKING ATTEMPT! Asking for file not in file list: " + sourceRelativePath + " (ipaddr="+ipaddr+")");
				}
				
				// Add it to the buffer
				String destinationRelativePath = request.getDestinationRelativePath();
				updateList.addInstallToBuffer(sourceRoot, sourceRelativePath, destinationRelativePath); // ZZZZZZZZZZZZZZ add dest to buffer
				log(sourceRoot, false, "  + "+destinationRelativePath);
			}
			else if (type == RequestType.END_OF_TRANSACTION)
			{
				// Commit request.
				updateList.addEndOfTransactionToBuffer();
				log(sourceRoot, false, "  End of transaction");
			}
			else if (type == RequestType.COMMIT)
			{
				// Commit request.
				updateList.addCommitToBuffer();
				log(sourceRoot, false, "  Commit");
			}
			else if (type == RequestType.ABORT)
			{
				// Abort request.
				updateList.addAbortToBuffer();
				log(sourceRoot, false, "  Abort");
			}
			else
			{
				log(sourceRoot, false, "  Unknown request type: " + type);
				throw new FipException("Unknown request type: " + type);
			}
		}
		
		// Get the passphrase for the destination uuid, and seal the buffer.
		String passphrase = sourceProperties.getPassphrase(destinationUuid);
		updateList.sealTheBuffer(txId, salt, passphrase);
		
		log(sourceRoot, false, "  - complete -");


		// Return the updates
		return updateList;
	}

	public void abortTransaction(String destinationRoot, String txId) throws FipException
	{
		log(destinationRoot, true, "Aborting transaction " + txId);
		FipServer_updateExecuter.abortTransaction(destinationRoot, txId);
	}
	
	public static void log(String sourceOrDestinationRoot, boolean prefixWithDate, String message) throws FipException
	{
		// Get the message ready - maybe add a date, check there is a newline on the end.
		if (prefixWithDate)
		{
			long now = System.currentTimeMillis();
			String nowDate = new Date(now).toString();
			message = nowDate + ": " + message;
		}
		if ( !message.endsWith("\n"))
			message += "\n";
		
		// Write to the log file
		FileWriter writer = null;
		try {
			String path = sourceOrDestinationRoot + "/" + Fip.PREFIX + "log";
			writer = new FileWriter(path, true);
			writer.write(message);
			writer.close();
		} catch (IOException e) {
			throw new FipException("Error writing to log file: " + e.toString());
		}
	}
}
