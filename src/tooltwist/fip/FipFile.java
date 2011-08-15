package tooltwist.fip;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import tooltwist.fip.FipRule.Op;

class FipFile implements Comparable<FipFile>, FipRuleParameter
{
	private String relativePath;
	private long lastModified;
	private long fileSize;
	private String hash;
	private boolean confirmExists = false;
	private boolean checkedForDelta;
	
	// Fields set by rules
	private String destinationRelativePath;
	private Op op = Op.INCLUDE;
	
//	ChecksumFile(String installRoot, String relativePath)
//	{
//		this.path = relativePath;
//		this.modifi
//	}

	public FipFile(String relativePath)
	{
		this.relativePath = relativePath;
		this.destinationRelativePath = relativePath;
	}

	public long getLastModified()
	{
		return lastModified;
	}

	public void setLastModified(long lastModified)
	{
		this.lastModified = lastModified;
	}

	public long getFileSize()
	{
		return fileSize;
	}

	public void setLength(long length)
	{
		this.fileSize = length;
	}

	public String getChecksum()
	{
		return hash;
	}

	public void setChecksum(String checksum)
	{
		this.hash = checksum;
	}

	public String getSourceRelativePath()
	{
		return relativePath;
	}

	public void calculateChecksum(File file) throws FipException
	{
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);
			byte[] buf = new byte[4096];
			MessageDigest m = MessageDigest.getInstance("MD5");
			for ( ; ; )
			{
				int len = fis.read(buf);
				if (len <= 0)
					break;
				m.update(buf, 0, len);
			}
			byte[] digest = m.digest();
			BigInteger bigInt = new BigInteger(1,digest);
			String hashtext = bigInt.toString(16);
			
			// Now we need to zero pad it if you actually want the full 32 chars.
			while (hashtext.length() < 32 ) {
			  hashtext = "0"+hashtext;
			}
//System.out.println("hash is " + hashtext);
			this.hash = hashtext;
		}
		catch (NoSuchAlgorithmException e)
		{
			FipException ex = new FipException("Could not find MD5 implementation: " + e.toString());
			ex.setStackTrace(e.getStackTrace());
			throw ex;
		}
		catch (IOException e)
		{
			FipException ex = new FipException("Error calculating MD5 hash: " + e.toString());
			ex.setStackTrace(e.getStackTrace());
			throw ex;
		}
		finally
		{
			if (fis != null)
				try { fis.close(); } catch (Exception e) { }
		}
	}

	public int compareTo(FipFile o)
	{
		return relativePath.compareTo(o.relativePath);
	}

	public void setConfirmExists()
	{
		this.confirmExists = true;
	}

	public boolean getConfirmExists()
	{
		return confirmExists;
	}

	public void setCheckedForDelta(boolean b)
	{
		this.checkedForDelta = true;
	}
	
	public boolean getCheckedForDelta()
	{
		return checkedForDelta;
	}

	public String getDestinationRelativePath()
	{
		return destinationRelativePath;
	}

	public void setDestinationRelativePath(String destinationRelativePath)
	{
		this.destinationRelativePath = destinationRelativePath;
	}

	public Op getOp()
	{
		return this.op;
	}

	public void setOp(Op op)
	{
		this.op = op;
	}

	
	public String toString()
	{
		return this.relativePath;
	}
}
