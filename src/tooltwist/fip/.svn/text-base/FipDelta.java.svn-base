package tooltwist.fip;

public class FipDelta
{
	public enum Type {
		START_BATCH("S"), NEW("N"), CHANGE("C"), DELETE("D"), END_BATCH("E"), ABORT_BATCH("A");
		
		String code;
		
		private Type(String code) {
			this.code = code;
		}
		
		public String getCode() {
			return code;
		}
		
		public static Type getType(String code)
		{
			for (Type t : Type.values())
				if (t.code.equals(code))
					return t;
			return null;
		}
	};
	private String sourceRelativePath;
	private String destinationRelativePath;
	private long filesize;
	private Type type;
	
	public FipDelta(String sourceRelativePath, String destinationRelativePath, long fileSize2, Type type)
	{
		this.sourceRelativePath = sourceRelativePath;
		this.destinationRelativePath = destinationRelativePath;
		this.filesize = fileSize2;
		this.type = type;
	}

	public Type getType()
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
	
	public String toString() {
		String str = type.getCode() + ":" + sourceRelativePath;
		if ( !sourceRelativePath.equals(destinationRelativePath))
			str += " -> " + destinationRelativePath;
		return str;
	}

	public long getFilesize()
	{
		return filesize;
	}
	
}
