package tooltwist.fip;

import java.io.IOException;

/**
 * This abstract class defines all the methods used by the main FIP class, related to
 * operations at either the source or the destination. There are two concrete classes that
 * support this interface: {@link FipThisMachineServerProxy}, for sources or destinations
 * on the current machine, and {@link FipHttpServerProxy} for sources or destinations on
 * a remote machine. For remote machines, the proxy communicates using on restful servlet
 * interface running on the remote machine.
 * 
 * @author philipcallender
 *
 */
public abstract class FipServerProxy
{
	private String root;
	private String uuid = null;
	
	public FipServerProxy(String rootPath)
	{
		this.root = rootPath;
	}

	/**
	 * Only run on the source machine.
	 * 
	 * @return
	 * @throws FipException
	 */
	public abstract String askForUuid() throws FipException;

	/**
	 * Only run on the destination machine.
	 * 
	 * @param sourceUuid
	 * @return
	 * @throws FipException
	 */
	public abstract NewTransactionReply startNewTransaction(String sourceUuid) throws FipException;


	public abstract NewTransactionReply askForTransactionDetails(String sourceUuid, String txId) throws FipException;

	/**
	 * Get a list of files below the root.
	 */
	public abstract FipList askForFileList(boolean isDestination) throws IOException, FipCorruptionException, FipException;

	/**
	 * Install: Transfer updates, to install on server
	 */
	public abstract void sendUpdates(String txId, FipBatchOfUpdates updateBuffer) throws FipException;

	/**
	 * Ask for a list of update instructions, containing specific updates.
	 */
	public abstract FipBatchOfUpdates askForUpdates(FipRequestList requestList, String destinationUuid, String txId, String salt) throws FipException, IOException, FipCorruptionException;

	/**
	 * Abort the transaction.
	 * @throws FipException 
	 */
	public abstract void abortTransaction(String txId) throws FipException;

	public String getRoot()
	{
		return root;
	}

	public String getUuid() throws FipException
	{
		if (this.uuid == null)
			this.uuid = askForUuid();
		return uuid;
	}

}
