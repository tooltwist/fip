package tooltwist.fip;

import java.io.IOException;

/**
 * This class provides an adaptor to let the source talk to the destination within the same process.
 * 
 * @author philipcallender
 *
 */
public class FipThisMachineServerProxy extends FipServerProxy
{
	private FipServer realServer = new FipServer();

	public FipThisMachineServerProxy(String root) throws FipException
	{
		super(root);
	}

	@Override
	public String askForUuid() throws FipException
	{
		String sourceRoot = this.getRoot();
		
		SourceProperties sourceProperties = new SourceProperties(sourceRoot, "localhost");
		return sourceProperties.getSourceUuid();
	}

	@Override
	public NewTransactionReply startNewTransaction(String sourceUuid) throws FipException
	{
		String destinationRoot = this.getRoot();
		return realServer.destination_startNewTransaction(sourceUuid, destinationRoot, "localhost");
	}

	@Override
	public NewTransactionReply askForTransactionDetails(String sourceUuid, String txId) throws FipException
	{
		String destinationRoot = this.getRoot();
		return realServer.destination_askForTransactionDetails(sourceUuid, destinationRoot, txId, "localhost");
	}

	@Override
	public void sendUpdates(String txId, FipBatchOfUpdates updateBuffer) throws FipException
	{
		realServer.destination_installBatchOfFiles(getRoot(), txId, updateBuffer, "localhost");
		updateBuffer.initializeEmptyBuffer();
	}

	@Override
	public FipBatchOfUpdates askForUpdates(FipRequestList requestList, String destinationUuid, String txId, String salt) throws FipException, IOException, FipCorruptionException
	{
		FipBatchOfUpdates updateInstructions = realServer.source_getRequestedUpdates(getRoot(), requestList, destinationUuid, txId, salt, "localhost");

		return updateInstructions;
	}

	@Override
	public FipList askForFileList(boolean isDestination) throws IOException, FipCorruptionException, FipException
	{
		FipList list = realServer.getFileList(getRoot(), isDestination, "localhost");
		return list;
	}

	@Override
	public void abortTransaction(String txId) throws FipException
	{
		realServer.abortTransaction(getRoot(), txId);
	}
}
