package tooltwist.fip;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Vector;

import tooltwist.fip.FipDelta.Type;
import tooltwist.fip.FipRule.Op;

public class FipList
{
	/**
	 * The manifest file is stored in the root of the directory hierarchy being updated.
	 * Note: don't confuse this with the file system root.
	 */
	private static final String FIP_MANIFEST = Fip.PREFIX + "manifest";
	private static final int INITIAL_CAPACITY = (2 * 1024 * 1024);
	private HashMap<String, FipFile> list = new HashMap<String, FipFile>(INITIAL_CAPACITY);
	private boolean listVariedFromRealFiles = false;
	
	public void syncWithRealFiles(File rootDirectory) throws FipException
	{
		// Check the list against the actual files
		loadDirectory_recursive(rootDirectory.getAbsolutePath(), rootDirectory);
		
		// Remove any files that don't exist now
		Vector<String> toRemove = new Vector<String>();
		for (FipFile f : list.values())
			if ( !f.getConfirmExists())
				toRemove.add(f.getSourceRelativePath());
		for (String key : toRemove)
		{
			this.list.remove(key);
			this.listVariedFromRealFiles = true;
		}
		
		// Maybe re-write the list file
		if (this.listVariedFromRealFiles)
			this.writeToFile(rootDirectory);
	}

	private void loadDirectory_recursive(String rootPath, File directory) throws FipException
	{
		if ( !rootPath.endsWith("/"))
			rootPath += "/";

		// Ignore files with these prefixes
		String p0 = FipServer_updateExecuter.FIP_FILE_PREFIX;
		String p1 = FipServer_updateExecuter.UNDERSTUDY_PREFIX_NEW_FILE;
		String p2 = FipServer_updateExecuter.UNDERSTUDY_PREFIX_CHANGE_FILE;
		String p3 = FipServer_updateExecuter.UNDERSTUDY_PREFIX_DELETE_FILE;
		String p4 = FipServer_updateExecuter.ROLLBACK_FILE_PREFIX + FipServer_updateExecuter.UNDERSTUDY_PREFIX_NEW_FILE;
		String p5 = FipServer_updateExecuter.ROLLBACK_FILE_PREFIX + FipServer_updateExecuter.UNDERSTUDY_PREFIX_CHANGE_FILE;
		String p6 = FipServer_updateExecuter.ROLLBACK_FILE_PREFIX + FipServer_updateExecuter.UNDERSTUDY_PREFIX_DELETE_FILE;


		for (File file : directory.listFiles())
		{
			String name = file.getName();
			if (name.startsWith(Fip.PREFIX))
				continue;
			if (file.isDirectory())
			{
				loadDirectory_recursive(rootPath, file);
				continue;
			}
			
			// See if it already exists
			String path = file.getAbsolutePath();
			if (path.endsWith("/" + FIP_MANIFEST))
				continue;
			if (name.startsWith(p0) || name.startsWith(p1) || name.startsWith(p2) || name.startsWith(p3) || name.startsWith(p4) || name.startsWith(p5) || name.startsWith(p6))
				continue;
			String relativePath = path.substring(rootPath.length());
			long length = file.length();
			long lastModified = file.lastModified();
			FipFile fipFile = list.get(relativePath);
			if (fipFile == null)
			{
				fipFile = new FipFile(relativePath);
				fipFile.setLastModified(lastModified);
				fipFile.setLength(length);
				fipFile.calculateChecksum(file);
				fipFile.setConfirmExists();
				this.list.put(relativePath, fipFile);
				this.listVariedFromRealFiles = true;
			}
			else if (fipFile.getLastModified() != lastModified)
			{
				// We have the file already. Check it's size and/or checksum
				fipFile.setLastModified(lastModified);
				fipFile.setLength(length);
				fipFile.calculateChecksum(file);
				fipFile.setConfirmExists();
				this.listVariedFromRealFiles = true;
			}
			else
			{
				// The file has not been modified - assume the length and checksum are correct.
				fipFile.setConfirmExists();
			}
		}
	}
	
	public String serialize(boolean showRuleDebugStuff)
	{
		// Create a sorted list
		Vector<FipFile> newList = new Vector<FipFile>();
		for (FipFile f : this.list.values())
			newList.add(f);
		Collections.sort(newList);
		
		// Write them out
		StringBuffer buf = new StringBuffer();
		for (FipFile f : newList)
		{
			// Note that the path must be at the end, because it might contain the separator character.
			String debugStuff = "";
			if (showRuleDebugStuff)
			{
				Op op = f.getOp();
				if (op == Op.EXCLUDE)
					debugStuff += " (EXCLUDE)";
				else if (op == Op.IGNORE)
					debugStuff += " (IGNORE)";
				String s = f.getSourceRelativePath();
				String d = f.getDestinationRelativePath();
				if ( !s.equals(d))
					debugStuff += " -> " + f.getDestinationRelativePath();
			}
			buf.append(f.getLastModified() + ":" + f.getFileSize() + ":" + f.getChecksum() + ":" + f.getSourceRelativePath() + debugStuff + "\n");
		}
		return buf.toString();
	}

	/**
	 * Write the contents of the list to a hidden file in the top directory
	 * @param directory
	 * @throws FileNotFoundException 
	 */
	public void writeToFile(File directory) throws FipException
	{
		String path = directory.getAbsolutePath() + File.separator + FIP_MANIFEST;
		try {
			PrintWriter pw = new PrintWriter(new FileOutputStream(path));
			pw.write(serialize(false));
			pw.close();
		} catch (FileNotFoundException e) {
			throw new FipException("Error writing fip index file: " + path + ": "+ e.toString());
		}
	}

	static FipList loadListFromFile(File directory) throws IOException, FipCorruptionException, FipException
	{
		if ( !directory.exists() || !directory.isDirectory())
			throw new FipException("Unknown directory");

		String fipFilePath = directory.getAbsolutePath() + File.separator + FIP_MANIFEST;
		try {
			BufferedReader in = new BufferedReader(new FileReader(fipFilePath));
			return deserialize(in);
		} catch (FipCorruptionException e) {
			e.setFilepath(fipFilePath);
			throw e;
		} catch (FileNotFoundException e) {
			// If the manifest file isn't found, that's okay. We'll return an empty list. 
			return new FipList();
		}
	}

	public static FipList deserialize(BufferedReader in) throws IOException, FipCorruptionException
	{
		FipList fipList = new FipList();
		for (int lineNo = 1; ; lineNo++)
		{
			String line = in.readLine();
			if (line == null)
				break;

			// Get lastModified
			int pos = line.indexOf(":");
			if (pos < 0)
				throw new FipCorruptionException(lineNo, "missing lastModified");
			String str = line.substring(0, pos);
			long lastModified;
			try {
				lastModified = Long.parseLong(str);
			} catch (NumberFormatException e) {
				throw new FipCorruptionException(lineNo, "non numeric value for lastModified");
			}
			line = line.substring(pos + 1);

			// Get the length
			pos = line.indexOf(":");
			if (pos < 0)
				throw new FipCorruptionException(lineNo, "missing length");
			str = line.substring(0, pos);
			long length = Integer.parseInt(str);
			try {
				length = Long.parseLong(str);
			} catch (NumberFormatException e) {
				throw new FipCorruptionException(lineNo, "non numeric value for length");
			}
			line = line.substring(pos + 1);

			// Get the Checksum
			pos = line.indexOf(":");
			if (pos < 0)
				throw new FipCorruptionException(lineNo, "missing relative path");
			String checksum = line.substring(0, pos);
			
			// Get the name. This must be at the end, because it might contain a colon.
			String path = line.substring(pos + 1);
			
			// Create the new record in the list
			FipFile fipFile = new FipFile(path);
			fipFile.setLastModified(lastModified);
			fipFile.setLength(length);
			fipFile.setChecksum(checksum);
			fipList.list.put(path, fipFile);
		}
		return fipList;
	}

	/**
	 * Compare two lists, and return a list of files to be updated and deleted.
	 * @param list2
	 * @return 
	 */
	FipDeltaList getDelta(FipList list2)
	{
		FipDeltaList deltaList = new FipDeltaList();
		
		// For each file in list1, check it is the same in list2
		for (FipFile f1 : this.files())
		{
			Op op = f1.getOp();
			if (op == Op.EXCLUDE)
				continue;
			if (op == Op.IGNORE)
				continue;
			long fileSize = f1.getFileSize();
			

//{
//String s = f1.getSourceRelativePath();
//String d = f1.getDestinationRelativePath();
//if ( !s.equals(d))
//{
//	int abc = 123;
//	int def = abc;
//}
//}
			String sourceRelativePath = f1.getSourceRelativePath();
			String destinationRelativePath = f1.getDestinationRelativePath();

			FipFile f2 = list2.list.get(destinationRelativePath);
			if (f2 == null)
				deltaList.addDelta(sourceRelativePath, destinationRelativePath, fileSize, FipDelta.Type.NEW);
			else
			{
				long length1 = f1.getFileSize();
				long length2 = f2.getFileSize();
				String checksum1 = f1.getChecksum();
				String checksum2 = f2.getChecksum();

				if (length2 != length1 || !checksum2.equals(checksum1))
					deltaList.addDelta(sourceRelativePath, destinationRelativePath, fileSize, FipDelta.Type.CHANGE);

				f2.setCheckedForDelta(true);
			}
		}

		// Look for records in list2, that were not checked (ie. are not in list1)
		for (FipFile f2 : list2.files())
		{
			if ( !f2.getCheckedForDelta())
			{
				String relativePath = f2.getSourceRelativePath();
				long fileSize = f2.getFileSize();
				deltaList.addDelta(relativePath, relativePath, fileSize, Type.DELETE);
			}
		}
		
		return deltaList;
	}

	Iterable<FipFile> files()
	{
		return this.list.values();
	}

	public boolean containsFile(String relativePath)
	{
		return this.list.containsKey(relativePath);
	}

	public int numFiles() {
		return list.size();
	}

}
