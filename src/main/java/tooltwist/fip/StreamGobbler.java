package tooltwist.fip;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamGobbler extends Thread
{
	private static Logger logger = LoggerFactory.getLogger(StreamGobbler.class);

	private InputStream is;
	private String type;
	private OutputStream os;

	private String processName;

	// If redirect is null, writes to the named file as well as logging.
	public StreamGobbler(String processName, InputStream is, String type, OutputStream redirect)
	{
		this.processName = processName;
		this.is = is;
		this.type = type;
		this.os = redirect;
	}

	public void run()
	{
		try
		{
			PrintWriter pw = null;
			if (os != null)
				pw = new PrintWriter(os);

			InputStreamReader isr = new InputStreamReader(is);
			BufferedReader br = new BufferedReader(isr);
			String line = null;
			while ((line = br.readLine()) != null)
			{
				if (pw != null)
					pw.println(line);
				logger.debug(processName +":" + type + " >" + line);
			}
			if (pw != null)
				pw.flush();
		}
		catch (IOException ioe)
		{
			ioe.printStackTrace();
		}
	}
}