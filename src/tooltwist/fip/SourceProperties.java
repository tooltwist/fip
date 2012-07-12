package tooltwist.fip;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;

public class SourceProperties
{
	private String sourceUuid = null;
	private HashMap<String, String> passphrases = new HashMap<String, String>(); // destinationUuid->passphrase

	public SourceProperties(String sourceRoot, String ipaddr) throws FipException
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
		
		// Check the source directoru and properties file.
		if ( !sourceRoot.endsWith("/"))
			sourceRoot += "/";
		File dir = new File(sourceRoot);
		if ( !dir.exists())
			throw new FipException("Error: Unknown fip directory " + sourceRoot + " (ipaddr: " + ipaddr+")");
		if ( !dir.isDirectory())
			throw new FipException("Error: fip directory is not a directory: " + sourceRoot + " (ipaddr: " + ipaddr+")");
		String propertiesFilePath = sourceRoot + ".fip-source";
		File file = new File(propertiesFilePath);
		if ( !file.exists())
			throw new FipException("Error: directory not a FIP source. Missing " + propertiesFilePath + " (ipaddr: " + ipaddr+")");
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

		// Get the uuid
		String tmpUuid = prop.getProperty("sourceUuid");
		if (tmpUuid == null || tmpUuid.equals(""))
			throw new FipException("Error: sourceUuid is not defined in " + propertiesFilePath);
		this.sourceUuid = tmpUuid;

		// All lines except the uuid line are expected to be uuid to pasphrase mappings. e.g.
		// 		C981BA34-821D-4EA5-B9C7-82946DD422B6=The quick brown fox
		for (Object key : prop.keySet())
		{
			if (key instanceof String)
			{
				String destinationUuid = (String) key;
				if (destinationUuid.equals("sourceUuid"))
					continue;
				String passphrase = prop.getProperty(destinationUuid);
				
				passphrases.put(destinationUuid, passphrase);
			}
		}
	}

	public String getSourceUuid()
	{
		return this.sourceUuid;
	}

	public String getPassphrase(String destinationUuid)
	{
		return passphrases.get(destinationUuid);
	}

}
