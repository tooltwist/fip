package tooltwist.fip;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;

public class TransactionProperties
{
	public static final String TRANSACTION_PROPERTIES_FILE = Fip.PREFIX + "txprops";
	private String txId;
	private String salt;
	private String ipaddr;
	private long expires;
	private TransactionStatus status;

	public static TransactionProperties loadTransactionProperties(String destinationRoot, String txId) throws FipException
	{
		TransactionProperties txprop = new TransactionProperties();
		txprop.txId = txId;
		
		// Look for a transaction with this Id, checking each possible status.
		String txDirectory = null;
		for (TransactionStatus status : TransactionStatus.values())
		{
			String path = transactionDirectory(destinationRoot, txId, status);
			File file = new File(path);
			if (file.exists() && file.isDirectory())
			{
				txprop.status = status;
				txDirectory = file.getAbsolutePath();
				break;
			}
		}
		if (txprop.status == null)
			throw new FipException("Unknown transaction " + txId + " at " +destinationRoot);
		
		String path = txDirectory + "/" + TRANSACTION_PROPERTIES_FILE;
		File file = new File(path);
		if ( !file.exists())
			throw new FipException("Error: Missing transaction properties file: " + path);
		if ( !file.canRead())
			throw new FipException("Error: No read permissions for " + path);
		
		// Load the properties
		Properties prop = new Properties();
		try {
			FileReader reader = new FileReader(file);
			prop.load(reader);
			reader.close();
		} catch (IOException e) {
			throw new FipException("Error: Could not read " + path + ": " + e.toString());
		}

		// Get the salt
		String tmpSalt = prop.getProperty("salt");
		if (tmpSalt == null || tmpSalt.equals(""))
			throw new FipException("Error: salt is not defined in " + path);
		txprop.salt = tmpSalt;

		// Get the ipaddr
		String tmpIpaddr = prop.getProperty("ipaddr");
		if (tmpIpaddr == null || tmpIpaddr.equals(""))
			throw new FipException("Error: ipaddr is not defined in " + path);
		txprop.ipaddr = tmpIpaddr;

		// Get the expires time
		String tmpExpires = prop.getProperty("expires");
		if (tmpExpires == null || tmpExpires.equals(""))
			throw new FipException("Error: expires is not defined in " + path);
		try {
			txprop.expires = Long.parseLong(tmpExpires);
		} catch (NumberFormatException e) {
			throw new FipException("Error: non-numeric expires time");
		}
		
		
		// If the expiry time has passed, make sure the status is "expired"
		long now = System.currentTimeMillis();
		if (txprop.expires < now && (txprop.status==TransactionStatus.PREPARING || txprop.status==TransactionStatus.READY_TO_COMMIT))
			txprop.changeStatus(destinationRoot, TransactionStatus.EXPIRED);

		// Return the properties.
		return txprop;
	}

	public void changeStatus(String destinationRoot, TransactionStatus newStatus) throws FipException
	{
		// Move the transaction directory
		String oldDirectory = transactionDirectory(destinationRoot, txId, this.status);
		String newDirectory = transactionDirectory(destinationRoot, txId, newStatus);
		File from = new File(oldDirectory);
		File to = new File(newDirectory);
		from.renameTo(to);
	}

	public static String transactionDirectory(String destinationRoot, String txId, TransactionStatus status)
	{
		if ( !destinationRoot.endsWith("/"))
			destinationRoot += "/";
		return destinationRoot + status.getPrefix() + txId;
	}

	private void saveProperties(String destinationRoot) throws FipException
	{
		String directory = transactionDirectory(destinationRoot, this.txId, this.status);
		String path = directory + "/" + TRANSACTION_PROPERTIES_FILE;

		PrintWriter writer = null;
		try {
			writer = new PrintWriter(path);
			writer.println("salt=" + salt);
			writer.println("ipaddr=" + ipaddr);
			writer.println("expires=" + expires);
			writer.close();
		}
		catch (FileNotFoundException e)
		{
			throw new FipException("Could not create " + path + ": " + e.toString());
		}
		finally
		{
			if (writer != null)
				try {writer.close();} catch (Exception e) { /* do nothing */ }
		}
	}

	public static void createPropertiesFile(String destinationRoot, String txId, TransactionStatus status, String salt, long expires, String ipaddr) throws FipException
	{
		TransactionProperties bp = new TransactionProperties();
		bp.txId = txId;
		bp.salt = salt;
		bp.expires = expires;
		bp.ipaddr = ipaddr;
		bp.status = status;
		bp.saveProperties(destinationRoot);
	}

	public String getSalt()
	{
		return salt;
	}

	public String getIpaddr()
	{
		return ipaddr;
	}

	public long getExpires()
	{
		return expires;
	}

	public TransactionStatus getStatus()
	{
		return status;
	}

}
