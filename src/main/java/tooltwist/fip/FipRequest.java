package tooltwist.fip;

public class FipRequest
{
	enum RequestType {
		UPDATE("U"), DELETE("D"), COMMIT("C"), ABORT("A"), END_OF_TRANSACTION("T");
		
		private String code;
		private RequestType(String code) { this.code = code; }
		public String getCode() { return code; }
		public static RequestType getType(String code) {
			for (RequestType i : RequestType.values()) {
				if (i.code.equals(code))
					return i;
			}
			return null;
		}
	}
	private static final String SEPARATOR = ":::";
	private RequestType type;
	private String sourceRelativePath;
	private String destinationRelativePath;

	private FipRequest(RequestType type, String sourceRelativePath, String destinationRelativePath)
	{
		this.type = type;
		this.sourceRelativePath = sourceRelativePath;
		this.destinationRelativePath = destinationRelativePath;
	}

	public static FipRequest newUpdateRequest(String sourceRelativePath, String destinationRelativePath)
	{
		return new FipRequest(RequestType.UPDATE, sourceRelativePath, destinationRelativePath);
	}

	public static FipRequest newDeleteRequest(String sourceRelativePath)
	{
		return new FipRequest(RequestType.DELETE, sourceRelativePath, sourceRelativePath);
	}

	public static FipRequest newEndOfTransactionMarkerRequest()
	{
		return new FipRequest(RequestType.END_OF_TRANSACTION, "", "");
	}

	public static FipRequest newCommitRequest()
	{
		return new FipRequest(RequestType.COMMIT, "", "");
	}

	public static FipRequest newAbortRequest()
	{
		return new FipRequest(RequestType.ABORT, "", "");
	}

	public String serialize()
	{
//		if (this.op == Op.DELETE)
//			return "D" + SEPARATOR + sourceRelativePath;
//		else
		String line = type.getCode() + SEPARATOR + sourceRelativePath + SEPARATOR + destinationRelativePath + "\n";
		return line;
//		else
//			return "D" + SEPARATOR;
//		else if (this.op == Op.DELETE)
//			return "D" + SEPARATOR + sourceRelativePath;
	}

	public static FipRequest deSerialize(String line) throws FipException
	{
		int pos = line.indexOf(SEPARATOR);
		if (pos != 1)
			throw new FipException("Invalid line in serialized FipRequestList");
		String code = line.substring(0, pos);
		RequestType op = RequestType.getType(code);
		line = line.substring(pos + SEPARATOR.length());

		if (op == null)
			throw new FipException("Invalid line in serialized FipRequestList");
		
//		if (op.equals("D"))
//		{
//			String sourceRelativePath = line;
//			return newDeleteRequest(sourceRelativePath);
//		}
//		else if (op.equals("U"))
//		{
			pos = line.indexOf(SEPARATOR);
			if (pos < 0)
				throw new FipException("Invalid line in serialized FipRequestList");
			String sourceRelativePath = line.substring(0, pos);
			String destinationRelativePath = line.substring(pos + SEPARATOR.length());
			
			return new FipRequest(op, sourceRelativePath, destinationRelativePath);
//			return newUpdateRequest(sourceRelativePath, destinationRelativePath);
//		}
	}

//	public boolean isDeleteRequest()
//	{
//		return deleteRequest;
//	}
	
	public RequestType getType()
	{
		return type;
	}

	public String getSourceRelativePath()
	{
		return sourceRelativePath;
	}

	public String getDestinationRelativePath()
	{
		return destinationRelativePath;
	}
	
	public String toString()
	{
		return this.type + ", " + sourceRelativePath + ", " + destinationRelativePath;
	}
}
