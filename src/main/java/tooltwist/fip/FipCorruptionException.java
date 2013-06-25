package tooltwist.fip;

public class FipCorruptionException extends Exception
{
	private static final long serialVersionUID = 5587717984977569508L;
	private String path;
	private int lineNo;
	private String msg;

	public FipCorruptionException(String fipFilePath, int errorLine, String errorMsg)
	{
		this.path = fipFilePath;
		this.lineNo = errorLine;
		this.msg = errorMsg;
	}

	public FipCorruptionException(int errorLine, String errorMsg)
	{
		this.lineNo = errorLine;
		this.msg = errorMsg;
	}

	public String toString()
	{
		String str = "Corrupt Fip file list";
		str += ": " + msg;
		if (path != null)
			str += ": " + path;
		str += ", line " + lineNo;
		return str;
	}

	public void setFilepath(String fipFilePath)
	{
		this.path = fipFilePath;
	}
}
