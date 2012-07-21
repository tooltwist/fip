package tooltwist.fip;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import java.util.Vector;
import java.util.regex.Pattern;

public class DestinationProperties
{
	
	public enum CommitMode {
		COMMIT_AS_A_TRANSACTION("transaction"),
		COMMIT_AFTER_EVERY_FILE("individual"),
		DO_NOT_COMMIT("manual");
		
		private String mode;
		
		private CommitMode(String mode) { this.mode = mode; }
		public String getMode() { return mode; }
		public static CommitMode findMode(String mode) throws FipException {
			for (CommitMode m: CommitMode.values())
				if (m.mode.equals(mode))
					return m;
			throw new FipException("Unknown commitMode: " + mode);
//			return COMMIT_AS_A_TRANSACTION;
		}
	};


	private String destinationUuid;
	private String passphrase;
	private String sourceUuid;
	private String preCommitCommand;
	private String postCommitCommand;
	private CommitMode commitMode = CommitMode.COMMIT_AS_A_TRANSACTION;
	private Vector<Pattern> protectPatterns = new Vector<Pattern>();

	public DestinationProperties(String destinationRoot, String ipaddr) throws FipException
	{
		// Check that the current user is not root. This isn't foolproof, as the user.name property
		// can be spoofed by defining it on the command line. It does however prevent innocent errors.
		String currentUser = System.getProperty("user.name");
		if (currentUser != null && currentUser.trim().equals("root"))
		{
			System.err.println("Fatal Error: Fip commands may not be run as root.");
			System.err.println("Exiting now.");
			System.exit(1);
		}
			
		
		// Check the directory and properties file for the destination.
		if ( !destinationRoot.endsWith("/"))
			destinationRoot += "/";
		File dir = new File(destinationRoot);
		if ( !dir.exists())
			throw new FipException("Error: Unknown fip directory " + destinationRoot + "(called from "+ipaddr+")");
		if ( !dir.isDirectory())
			throw new FipException("Error: fip directory is not a directory: " + destinationRoot + "(called from "+ipaddr+")");
		String propertiesFilePath = destinationRoot + ".fip-destination";
		File file = new File(propertiesFilePath);
		if ( !file.exists())
			throw new FipException("Error: Directory not a fip destination. Missing " + propertiesFilePath + "(called from "+ipaddr+")");
		if ( !file.canRead())
			throw new FipException("Error: No read permissions for " + propertiesFilePath);

		// Check that the current user owns the properties file. Java isn't much good at working
		// this out, so we check this by setting the file permissions to a nice secure value.
		boolean iCanWrite = file.canWrite();
		if (
				!file.setExecutable(false, false) // nobody can execute
				|| !file.setReadable(false, false) // don't let anybody read
				|| !file.setReadable(true) // let owner (me) read
				|| !file.setWritable(false, false) // don't let anybody write
				|| !file.setWritable(iCanWrite) // same as before
		)
			throw new FipException("Error: cannot set property file permissions (maybe not the owner?): " + propertiesFilePath);
		
		// Load the properties
		Properties prop = new Properties();
		try {
			FileReader reader = new FileReader(file);
			prop.load(reader);
			reader.close();
		} catch (IOException e) {
			throw new FipException("Error: Could not read " + propertiesFilePath + ": " + e.toString());
		}

		// Get the uuid for this destination
		String tmpUuid = prop.getProperty("destinationUuid");
		if (tmpUuid == null || tmpUuid.equals(""))
			throw new FipException("Error: destinationUuid is not defined in " + propertiesFilePath);
		this.destinationUuid = tmpUuid;

		// Get the passphrase for this destination
		String tmpPassphrase = prop.getProperty("passphrase");
		if (tmpPassphrase == null || tmpPassphrase.equals(""))
			throw new FipException("Error: passphrase is not defined in " + propertiesFilePath);
		this.passphrase = tmpPassphrase;

		// Get the source uuid
		String tmpSourceUuid = prop.getProperty("sourceUuid");
		if (tmpSourceUuid == null || tmpSourceUuid.equals(""))
			throw new FipException("Error: sourceUuid is not defined in " + propertiesFilePath);
		this.sourceUuid = tmpSourceUuid;

		// Get the commit mode
		String commitModeStr = prop.getProperty("commitMode");
		if (commitModeStr != null && !commitModeStr.equals(""))
			this.commitMode = CommitMode.findMode(commitModeStr);

		// Get the preCommitCommand
		String preCommitCommand = prop.getProperty("preCommitCommand");
		if (preCommitCommand != null && !preCommitCommand.equals(""))
			this.preCommitCommand = preCommitCommand;

		// Get the postCommitCommand
		String postCommitCommand = prop.getProperty("postCommitCommand");
		if (postCommitCommand != null && !postCommitCommand.equals(""))
			this.postCommitCommand = postCommitCommand;
		
		// Get the protected files and directories
		String protectStr = prop.getProperty("protect");
		if (protectStr != null)
		{
			for ( ; ; )
			{
				int pos = protectStr.indexOf(",");
				if (pos < 0)
				{
					String str = protectStr.trim();
					if ( !str.equals(""))
					{
						Pattern pattern = Pattern.compile(str);
						this.protectPatterns.add(pattern);
					}
					break;
				}
				else
				{
					String str = protectStr.substring(0, pos).trim();
					protectStr = protectStr.substring(pos + 1);
					if ( !str.equals(""))
					{
						Pattern pattern = Pattern.compile(str);
						this.protectPatterns .add(pattern);
					}
				}
			}
		}
	}

	/**
	 * See if this path is matches a pattern in the "protect" property.
	 * @param relativePath
	 * @return
	 */
	public boolean isProtected(String relativePath)
	{
		for (Pattern i : this.protectPatterns)
			if (i.matcher(relativePath).matches())
				return true;
		return false;
	}

	public String getDestinationUuid()
	{
		return destinationUuid;
	}

	public String getPassphrase()
	{
		return passphrase;
	}

	public String getSourceUuid()
	{
		return sourceUuid;
	}

	public CommitMode getCommitMode()
	{
		return commitMode;
	}

	public String getPreCommitCommand()
	{
		return preCommitCommand;
	}

	public String getPostCommitCommand()
	{
		return postCommitCommand;
	}

}
