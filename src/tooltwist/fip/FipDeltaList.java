package tooltwist.fip;

import java.util.Vector;

import tooltwist.fip.FipDelta.Type;

public class FipDeltaList
{
	private Vector<FipDelta> list = new Vector<FipDelta>();
	
	public FipDeltaList()
	{
	}

	public void addDelta(String sourceRelativePath, String destinationRelativePath, long fileSize, Type type)
	{
		FipDelta fipDelta = new FipDelta(sourceRelativePath, destinationRelativePath, fileSize, type);
		list.add(fipDelta);
	}
	
	public Iterable<FipDelta> list()
	{
		return list;
	}

	public String listDeltas()
	{
		StringBuffer buf = new StringBuffer();
		// The first line contains: txId:salt
		// Subsequent lines contain: type:filesize:relativePath 
//		buf.append(this.txId + ":" + this.saltForNextServerCommunication + "\n");
		if (list.isEmpty())
			buf.append("No changes required");
		else
		{
			for (FipDelta delta : list)
			{
				String sourceRelativePath = delta.getSourceRelativePath();
				String destinationRelativePath = delta.getDestinationRelativePath();
				String code = delta.getType().getCode();
				if (code.equals("N"))
					buf.append("   new: " + sourceRelativePath);
				else if (code.equals("D")) {
					// This inaccurately reports protected files at the destination will be deleted
					//buf.append("delete: " + sourceRelativePath);
				} else if (code.equals("C"))
					buf.append("change: " + sourceRelativePath);
				else
					buf.append("      " + code + ": " + sourceRelativePath);
					
//				buf.append(delta.getType().getCode() + ":" + delta.getFilesize() + ":" + sourceRelativePath);
				if ( !sourceRelativePath.equals(destinationRelativePath))
					buf.append(" -------> " + destinationRelativePath);
				buf.append("\n");
			}
		}
		return buf.toString();
	}

	public boolean areChanges()
	{
		return !list.isEmpty();
	}

//	public static FipDeltaList deserialize(BufferedReader in) throws IOException, FipCorruptionException, FipException
//	{
//		FipDeltaList list = new FipDeltaList();
//
//		// The first line contains: txId:salt
//		String line = in.readLine();
//		if (line == null)
//			throw new FipCorruptionException(1, "Empty delta list");
//		// Get the delta type
//		int pos = line.indexOf(":");
//		if (pos < 0)
//			throw new FipCorruptionException(1, "Corrupt first line");
//		list.txId = line.substring(0, pos);
//		list.saltForNextServerCommunication = line.substring(pos + 1);
//		
//		
//		// Subsequent lines contain: type:filesize:relativePath 
//		for (int lineNo = 2; ; lineNo++)
//		{
//			line = in.readLine();
//			if (line == null)
//				break;
//
//			// Get the delta type
//			pos = line.indexOf(":");
//			if (pos < 0)
//				throw new FipCorruptionException(lineNo, "missing delta type");
//			String typeCode = line.substring(0, pos);
//			line = line.substring(pos + 1);
//			
//			// Get the file size
//			pos = line.indexOf(":");
//			if (pos < 0)
//				throw new FipCorruptionException(lineNo, "missing file size");
//			String filesizeStr = line.substring(0, pos);
//			int filesize;
//			try {
//				filesize = Integer.parseInt(filesizeStr);
//			} catch (NumberFormatException e) {
//				throw new FipException("Invalid file size: " + filesizeStr);
//			}
//			line = line.substring(pos + 1);
//
//			// Get the Path
//			String relativePath = line;
//			
//			// Create the new record in the list
//			list.addDelta(relativePath, relativePath, filesize, Type.getType(typeCode));
//		}
//		return list;
//	}

}
