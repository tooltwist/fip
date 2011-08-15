package tooltwist.fip;

/**
 * This class is used to return parameters from the FipServer.newTransaction() method.
 * @author philipcallender
 *
 */
public class NewTransactionReply
{
	private String destinationUuid;
	private String txId;
	private String salt;

	public NewTransactionReply(String destinationUuid, String txId, String salt)
	{
		super();
		this.destinationUuid = destinationUuid;
		this.txId = txId;
		this.salt = salt;
	}

	public String getDestinationUuid()
	{
		return destinationUuid;
	}

	public String getTxId()
	{
		return txId;
	}

	public String getSalt()
	{
		return salt;
	}

}
