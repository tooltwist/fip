package tooltwist.fip;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;

/**
 * This class is used to create batches of updates to be sent from the source
 * through to the destination. These updates are pushed into a byte buffer
 * which is later unpacked by class {@link FipServer_updateExecuter}.
 * 
 * @author philipcallender
 *
 */
public class FipBatchOfUpdates
{
	// Byte markers inserted into the buffer.
	// Magic numbers are inserted at strategic places for error detection.
	public static final byte MAGIC_START_OF_TRANSFER_FILE = 0x7c;
	public static final byte MAGIC_END_OF_BATCH = 0x2d;
//	public static final byte VERSION_MAJOR_NUMBER = 0x00;
//	public static final byte VERSION_MINOR_NUMBER = 0x01;
	public static final byte OP_INSTALL_FILE = 0x51;
	public static final byte OP_DELETE_FILE = 0x52;
	public static final byte OP_END_OF_TRANSACTION = 0x53;
	public static final byte OP_COMMIT_TRANSACTION = 0x54;
	public static final byte OP_ABORT_TRANSACTION = 0x55;
	public static final byte OP_REQUEST_FILE = 0x71;
	public static final byte MAGIC_BEFORE_STRING = -0x7a;
	public static final byte MAGIC_AFTER_STRING = 0x56;
	public static final byte MAGIC_BEFORE_FILE_CONTENTS = -0x12;
	public static final byte MAGIC_AFTER_FILE_CONTENTS = -0x57;
	public static final byte MAGIC_AFTER_DELETE = 0x72;
	public static final byte MAGIC_AFTER_REQUEST = 0x58;
	public static final int SEAL_LENGTH = 20; // The length of a SHA-1 hash placed on the end of the buffer
	
	// Return values
	public enum BufferStatus { WILL_FIT, WILL_NOT_FIT, IMPOSSIBLE_TO_SEND_BIGGER_THAN_BUFFER };
	
	// Recommended
	static int BUFFER_LENGTH = 50 * 1024 * 1024; // 50mb - absolute maximum file size.
	static final int SMALL_ENOUGH_TO_PUT_BIG_FILE_ON_TOP = 100 * 1024; // If the amount in the buffer it's less than this, it's not really worth sending by itself.
	static final int PREFERRED_MAX_TRANSMISSION = 1024*1024 * 2;	// 2mb - we'll try to keep the amount sent in each call to the server somewhere around this number.
//	private static final int SMALL_ENOUGH_TO_PUT_BIG_FILE_ON_TOP = 10000;
//	private static final int PREFERRED_MAX_TRANSMISSION = 100000;
	protected static final byte FLAG_EXECUTABLE = 0x01;

	private byte[] buf = new byte[BUFFER_LENGTH]; // An array of buffers
	private int nextPos = 0;
	private int numInstallsInBuffer = 0;
	private int numDeletesInBuffer = 0;
	private boolean bufferIsSealed = false;
	

	protected FipBatchOfUpdates() throws FipException
	{
//		this.srcPath = srcRoot;
//		this.destinationPath = dstRoot;
//		this.txId = txId;
		initializeEmptyBuffer();
	}
	
	public FipBatchOfUpdates(byte[] contents) throws FipException
	{
		if (contents.length > buf.length)
			throw new FipException("Buffer initializer too long");
		System.arraycopy(contents, 0, this.buf, 0, contents.length);
		this.nextPos = contents.length;
		this.bufferIsSealed = true;
		
		// Count the updates and deletes
	}

	public void initializeEmptyBuffer() throws FipException
	{
		nextPos = 0;
		bufferIsSealed = false;
		addToBuffer(MAGIC_START_OF_TRANSFER_FILE);
		addToBuffer(Fip.MAJOR_VERSION_NUMBER);
		addToBuffer(Fip.MINOR_VERSION_NUMBER);
		addToBuffer(MAGIC_END_OF_BATCH);
//		addStringToBuffer(destinationPath);
//		addStringToBuffer(txId);
		numInstallsInBuffer = 0;
		numDeletesInBuffer = 0;
	}

	private void addToBuffer(byte b) throws FipException
	{
		if (bufferIsSealed)
			throw new FipException("Internal error: Buffer is already sealed");
		buf[nextPos++] = b;
	}
	private void addToBuffer(byte[] b, int length) throws FipException
	{
		if (bufferIsSealed)
			throw new FipException("Internal error: Buffer is already sealed");
		for (int i = 0; i < length; i++)
			buf[nextPos++] = b[i];
	}

	private void addFileLengthToBuffer(long length) throws FipException
	{
		byte l1 = (byte) (length % 256);
		length /= 256;
		byte l2 = (byte) (length % 256);
		length /= 256;
		byte l3 = (byte) (length % 256);
		length /= 256;
		byte l4 = (byte) (length % 256);
		
		// Hi order bits first
		addToBuffer(l4);
		addToBuffer(l3);
		addToBuffer(l2);
		addToBuffer(l1);
	}

	private void addStringToBuffer(String string) throws FipException
	{
		byte[] bytes = string.getBytes();
		int tmp = bytes.length;
		byte l1 = (byte) (tmp % 256);
		tmp /= 256;
		byte l2 = (byte) (tmp % 256);
		
		// Hi order bits first
		addToBuffer(MAGIC_BEFORE_STRING);
		addToBuffer(l2);
		addToBuffer(l1);
		addToBuffer(bytes, bytes.length);
		addToBuffer(MAGIC_AFTER_STRING);
	}

	public void addInstallToBuffer(String sourceRoot, String sourceRelativePath, String destinationRelativePath) throws IOException, FipException, FipCorruptionException
	{
		String path = sourceRoot + File.separator + sourceRelativePath;
		File file = new File(path);
		if ( !file.exists())
			throw new FipException("Unknown file: " + path);
		if ( !file.isFile())
			throw new FipException("Invalid file: " + path);
		
//int pos1 = nextPos;
		// Add the operation
		removeTerminator();
		addToBuffer(OP_INSTALL_FILE);
		// Add the destination file path
		addStringToBuffer(destinationRelativePath);
		// Add the executable flag
		byte flags = 0x0;
		flags |= file.canExecute() ? FLAG_EXECUTABLE : 0;
		addToBuffer(flags);
		// Add the file length
		int length = (int) file.length();
		addFileLengthToBuffer(length);
		// Add the file contents
		addToBuffer(MAGIC_BEFORE_FILE_CONTENTS);
		FileInputStream is = new FileInputStream(file);
		if (is.read(buf, nextPos, length) != length)
		{
			throw new FipException("Error reading " + length + " bytes from: " + file.getAbsolutePath());
		}
		is.close();
		this.nextPos += length;
		addToBuffer(MAGIC_AFTER_FILE_CONTENTS);
		numInstallsInBuffer++;
		addTerminator();
//pos2 = nextPos;
//size = pos2 - pos1;
//System.out.println(sourceRelativePath + " took up " + size);
	}
	
	@Deprecated
	public void addDeletes(Iterable<String> deleteList) throws FipException
	{
		removeTerminator();
		for (String relativePath : deleteList)
		{
			// Add the operation
			addToBuffer(OP_DELETE_FILE);
			// Add the file path
			addStringToBuffer(relativePath);
			// Add the file contents
			addToBuffer(MAGIC_AFTER_DELETE);
			numDeletesInBuffer++;
		}
		addTerminator();			
	}

	public void addDeleteToBuffer(String relativePath) throws FipException
	{
		removeTerminator();
		// Add the operation
		addToBuffer(OP_DELETE_FILE);
		// Add the file path
		addStringToBuffer(relativePath);
		// Add the file contents
		addToBuffer(MAGIC_AFTER_DELETE);
		numDeletesInBuffer++;
		addTerminator();
	}
	
	public void addAskForFileToBuffer(String relativePath) throws FipException
	{
		removeTerminator();
		// Add the operation
		addToBuffer(OP_REQUEST_FILE);
		// Add the file path
		addStringToBuffer(relativePath);
		// Add the file contents
		addToBuffer(MAGIC_AFTER_REQUEST);
		addTerminator();
	}

	
	public boolean bufferContainsSomethingToSend()
	{
		return (numInstallsInBuffer > 0 || numDeletesInBuffer > 0);
	}

	private void addTerminator() throws FipException
	{
		addToBuffer(MAGIC_END_OF_BATCH);
	}

	private void removeTerminator() throws FipException
	{
		if (buf[nextPos-1] != MAGIC_END_OF_BATCH)
			throw new FipException("Update file missing end-of-transfer-marker");
		nextPos--;
	}

	public void addEndOfTransactionToBuffer() throws FipException
	{
		removeTerminator();
		addToBuffer(OP_END_OF_TRANSACTION);
		addTerminator();
	}

	public void addCommitToBuffer() throws IOException, FipCorruptionException, FipException
	{
		removeTerminator();
		addToBuffer(OP_COMMIT_TRANSACTION);
		addTerminator();
	}

	public void addAbortToBuffer() throws IOException, FipCorruptionException, FipException
	{
		initializeEmptyBuffer();
		removeTerminator();
		addToBuffer(OP_ABORT_TRANSACTION);
		addTerminator();
	}
	
	public byte[] getBuffer()
	{
		return buf;
	}
	
	public int getLength()
	{
		return nextPos;
	}

	public boolean containsUpdates()
	{
		if (numInstallsInBuffer > 0 && numDeletesInBuffer > 0)
			return true;
		// If there's more than an empty buffer, it must be some sort of instruction
		if (nextPos > 4)
			return true;
		return false;
	}

	public int getNumInstallsInBuffer()
	{
		return numInstallsInBuffer;
	}
	
	public int getNumDeletesInBuffer()
	{
		return numDeletesInBuffer;
	}

	public String toString()
	{
		return "Send buffer, " + nextPos + " bytes";
	}
	
	public boolean isSealed()
	{
		return this.bufferIsSealed;
	}

	/**
	 * Place a hash on the end of the buffer, based upon the data, the txId, the salt, and the passphrase.
	 * @throws FipException 
	 */
	public void sealTheBuffer(String txId, String salt, String passphrase) throws FipException
	{
		if (bufferIsSealed)
			throw new FipException("Internal error: Buffer is already sealed");

		try {
			MessageDigest m = MessageDigest.getInstance("SHA-1");
			m.update(buf, 0, getLength());
			m.update(txId.getBytes("iso-8859-1"));
			m.update(salt.getBytes("iso-8859-1"));
			m.update(passphrase.getBytes("iso-8859-1"));
			byte[] digest = m.digest();
			if (digest.length != SEAL_LENGTH)
				throw new FipException("Seal hash value is not the expected length");
			addToBuffer(digest, SEAL_LENGTH);
			this.bufferIsSealed  = true;

			// For debugging only
			BigInteger bigInt = new BigInteger(1,digest);
			String hashtext = bigInt.toString(16);
			while (hashtext.length() < 40) {
			  hashtext = "0"+hashtext;
			}
//			System.out.println("hash is " + hashtext);
		} catch (Exception e) {
			FipException fipException = new FipException("Could not seal buffer: " + e.toString());
			fipException.setStackTrace(e.getStackTrace());
			throw fipException;
		}
	}
}
