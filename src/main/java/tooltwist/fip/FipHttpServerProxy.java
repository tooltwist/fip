package tooltwist.fip;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.ConnectException;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.log4j.Logger;


public class FipHttpServerProxy extends FipServerProxy
{
	private static Logger logger = Logger.getLogger(FipHttpServerProxy.class);
	private String host;
	private int port;

	public FipHttpServerProxy(String host, int port, String root) throws FipException
	{
		super(root);
		this.host = host;
		this.port = port;
	}

	@Override
	public String askForUuid() throws FipException
	{
		String url = "http://" + host + ":" + port + "/getSourceUuid?sourcePath=" + this.getRoot();

		// Set the timeout parameters
		HttpClientParams params = new HttpClientParams();
		params.setConnectionManagerTimeout(30000);
		params.setSoTimeout(30000);
		
		// Send the request
		HttpClient client = new HttpClient(params);
//		HttpConnectionManager connectionManager = client.getHttpConnectionManager();
//		connectionManager.getParams().setSendBufferSize(50 * 1024);
//		connectionManager.getParams().setReceiveBufferSize(50 * 1024);
		GetMethod getMethod = new GetMethod(url);
		HttpMethodParams params2 = getMethod.getParams();
		params2.setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(3, false));
//		params2.setParameter("sourcePath", this.getRoot());
		try {
			int statusCode = client.executeMethod(getMethod);
			
			if (statusCode != HttpStatus.SC_OK)
			{
				// Error ZZZZZZZZZ
				throw new FipException("Unknown response from FIP server: " + statusCode + " (" + HttpStatus.getStatusText(statusCode) + ")");
			}
			else
			{
				// The server responded okay. Read the response.
				InputStream stream = null;
		    	try {
					stream = getMethod.getResponseBodyAsStream();
					Properties properties = new Properties();
					properties.load(stream);
					String uuid = properties.getProperty("uuid");
					if (uuid==null || uuid.equals(""))
						throw new FipException("Error: remote server did not return uuid.");
					return uuid;
		    	} finally {
		    		if (stream != null)
		    			try { stream.close(); } catch (Exception e) { /*  do nothing */ }
		    	}
				
			}
	    } catch (Exception ex) {
	    	logger.info("ERROR: " + ex.getClass().getName() + " "+ ex.getMessage());
	        ex.printStackTrace();
	        
	        FipException exception = new FipException(ex.toString());
	        exception.setStackTrace(ex.getStackTrace());
	        throw exception;
	    } finally {
	    	getMethod.releaseConnection();
	    }
	}

	@Override
	public NewTransactionReply startNewTransaction(String sourceUuid) throws FipException
	{
		String url = "http://" + host + ":" + port + "/startTransaction?sourceUuid=" + sourceUuid + "&destinationRoot=" + this.getRoot();

		// Prepare the timeout parameters
		HttpClientParams params = new HttpClientParams();
		params.setConnectionManagerTimeout(30 * 1000); // 30 seconds to connect
		params.setSoTimeout(30 * 1000); // 30 seconds to start the transaction
		
		// Request the new transaction
		HttpClient client = new HttpClient(params);
		GetMethod getMethod = new GetMethod(url);
		HttpMethodParams params2 = getMethod.getParams();
		params2.setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(3, false));
		try {
			int statusCode = client.executeMethod(getMethod);
			
			if (statusCode != HttpStatus.SC_OK)
			{
				// Error ZZZZZZZZZ
				throw new FipException("Unknown response from FIP server: " + statusCode + " (" + HttpStatus.getStatusText(statusCode) + ")");
			}
			else
			{
				// The server responded okay. Read the response.
				InputStream stream = null;
		    	try {
					stream = getMethod.getResponseBodyAsStream();
					Properties properties = new Properties();
					properties.load(stream);
					
					String txId = properties.getProperty("txId");
					if (txId==null || txId.equals(""))
						throw new FipException("Error: remote server did not return txId.");
					String destinationUuid = properties.getProperty("destinationUuid");
					if (destinationUuid==null || destinationUuid.equals(""))
						throw new FipException("Error: remote server did not return destinationUuid.");
					String salt = properties.getProperty("salt");
					if (salt==null || salt.equals(""))
						throw new FipException("Error: remote server did not return salt.");
					NewTransactionReply reply = new NewTransactionReply(destinationUuid, txId, salt);
					return reply;
		    	} finally {
		    		if (stream != null)
		    			try { stream.close(); } catch (Exception e) { /*  do nothing */ }
		    	}
				
			}
	    } catch (ConnectException ex) {
	    	String msg = "Could not connect to the remote server. Is it running on the specified machine/port?";
		logger.info("ERROR: " + msg);
	        FipException exception = new FipException(msg);
	        exception.setStackTrace(ex.getStackTrace());
	        throw exception;
	    } catch (Exception ex) {
	    	logger.info("ERROR: " + ex.getClass().getName() + " "+ ex.getMessage());
	        ex.printStackTrace();
	        
	        FipException exception = new FipException(ex.toString());
	        exception.setStackTrace(ex.getStackTrace());
	        throw exception;
	    } finally {
	    	getMethod.releaseConnection();
	    }
	}

	@Override
	public NewTransactionReply askForTransactionDetails(String sourceUuid, String txId) throws FipException
	{
		throw new FipException("I don't like it!");
//		return realServer.startNewTransaction(sourceUuid);
	}

	@Override
	public FipList askForFileList(boolean isDestination) throws IOException, FipCorruptionException, FipException
	{
		String url = "http://" + host + ":" + port + "/getFileList?path=" + this.getRoot() + "&isDestination=" + (isDestination?"Y":"N");

		// Prepare timeouts
		HttpClientParams params = new HttpClientParams();
		params.setConnectionManagerTimeout(30 * 1000); // 30 seconds
		params.setSoTimeout(10 * 60 * 1000); // 10 minutes
		
		// Call the server
		HttpClient client = new HttpClient(params);
		GetMethod getMethod = new GetMethod(url);
		HttpMethodParams params2 = getMethod.getParams();
		params2.setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(3, false));
//		params2.setParameter("root", this.getRoot());
		try {
			int statusCode = client.executeMethod(getMethod);
			
			if (statusCode != HttpStatus.SC_OK)
			{
				// Error ZZZZZZZZZ
				throw new FipException("Unknown response from FIP server: " + statusCode);
			}
			else
			{
				// The server responded okay. Check the response type.
				Header contentType = getMethod.getResponseHeader("Content-Type");
				if (contentType==null || !contentType.getValue().equals("application/zip"))
					throw new FipException("Invalid reply from FIP server");
				
				// Decompress the zip file.
		    	ZipInputStream zip = new ZipInputStream(getMethod.getResponseBodyAsStream());
		    	try {
			    	for ( ; ; )
			    	{
			    		ZipEntry entry = zip.getNextEntry();
			    		if (entry == null)
			    			break;
			    		String filePath = entry.getName();
			    		if ( !filePath.equals("data"))
			    			continue;
			    		
			    		// Found the right entry, read it now.
			    		byte[] buf = new byte[256 * 1024];
			    		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			    		for ( ; ; )
			    		{
				    		int len = zip.read(buf);
			    			if (len <= 0)
			    				break;
			    			byteArrayOutputStream.write(buf, 0, len);
			    		}
		    		
			    		// Convert the buffer to a file list 
			    		String reply = new String(byteArrayOutputStream.toByteArray());
						BufferedReader in = new BufferedReader(new StringReader(reply));
						FipList fipList = FipList.deserialize(in);
						return fipList;
			    	}
			    	throw new FipException("Zip file returned by the server("+this.host+":"+this.port+") contains no file list");
		    	} finally {
		    		zip.close();
		    	}
				
			}
	    } catch (Exception ex) {
	    	logger.info("ERROR: " + ex.getClass().getName() + " "+ ex.getMessage());
	        ex.printStackTrace();
	        
	        FipException exception = new FipException(ex.toString());
	        exception.setStackTrace(ex.getStackTrace());
	        throw exception;
	    } finally {
	    	getMethod.releaseConnection();
	    }
	}

//	@Override
//	@Deprecated
//	public FipDeltaList askForInstallDeltaList(FipList filesAtSource) throws IOException, FipCorruptionException, FipException
//	{
//		// TODO Auto-generated method stub
//		return null;
//	}

	/**
	 * SOURCE: Ask for specific updates, from a remote source. 
	 */
	@Override
	public FipBatchOfUpdates askForUpdates(FipRequestList requestList, String destinationUuid, String txId, String salt) throws FipException, IOException, FipCorruptionException
	{
		String url = "http://" + host + ":" + port + "/askForBatch";
        PostMethod postMethod = new PostMethod(url);
        try {
    		// Serialize the request, and zip it.
    		byte[] byteArray = requestList.serialize().getBytes();
//    		int len1 = byteArray.length;
    		byte[] compressed = Fip.zipIt(byteArray, byteArray.length);
//    		int len2 = compressed.length;
    		
    		// Add the zipped request and other parameters to the request
    		ByteArrayPartSource partSource = new ByteArrayPartSource("data.zipped", compressed);		
            Part[] parts = {
        			new FilePart("data.zipped", partSource),
        			new StringPart("sourcePath", this.getRoot(), "ISO-8859-1"),
        			new StringPart("destinationUuid", destinationUuid, "ISO-8859-1"),
        			new StringPart("txId", txId, "ISO-8859-1"),
        			new StringPart("salt", salt, "ISO-8859-1"),
            };
            postMethod.setRequestEntity(new MultipartRequestEntity(parts, postMethod.getParams()));
            
    		// Prepare timeouts
    		HttpClientParams params = new HttpClientParams();
    		params.setConnectionManagerTimeout(30 * 1000); // 30 seconds
    		params.setSoTimeout(10 * 60 * 1000); // 10 minutes
    		
            // Call the remote servlet
            HttpClient client = new HttpClient(params);
//            client.getHttpConnectionManager().getParams().setConnectionTimeout(3000);
            int statusCode = client.executeMethod(postMethod);

            // Check the reply
            if (statusCode != HttpStatus.SC_OK)
			{
				// Error ZZZZZZZZZ
				throw new FipException("Unknown response from server: " + statusCode + ": " + HttpStatus.getStatusText(statusCode));
			}
			else
			{
				// The server responded okay. Check the response type.
				Header contentType = postMethod.getResponseHeader("Content-Type");
				if (contentType==null || !contentType.getValue().equals("application/zip"))
					throw new FipException("Invalid reply from FIP server");
				
				// Decompress the zip file and convert it to an instructions buffer.
				byte[] updates = Fip.unzipIt(postMethod.getResponseBodyAsStream(), "data");
				FipBatchOfUpdates instructionsBuffer = new FipBatchOfUpdates(updates);
				return instructionsBuffer;
				
			}
	    } catch (Exception ex) {
	    	// Write a message to the log file ZZZZZZZZZZZZZZZZZZZZ
	    	logger.info("ERROR: " + ex.getClass().getName() + " "+ ex.getMessage());
	        ex.printStackTrace();
	        
	        // Throw an exception
	        FipException exception = new FipException(ex.toString());
	        exception.setStackTrace(ex.getStackTrace());
	        throw exception;
	    } finally {
	    	postMethod.releaseConnection();
	    }
	}

	/**
	 * DESTINATION: Send updates to a remote destination.
	 */
	@Override
	public void sendUpdates(String txId, FipBatchOfUpdates updateBuffer) throws FipException
	{
		String url = "http://" + host + ":" + port + "/installBatch";
		PostMethod postMethod = new PostMethod(url);

		// Get the file requests
        try {
			byte[] byteArray = updateBuffer.getBuffer();
			int length = updateBuffer.getLength();
			byte[] compressed = Fip.zipIt(byteArray, length);
//int len2 = compressed.length;
		

//        filePost.getParams().setBooleanParameter(HttpMethodParams.USE_EXPECT_CONTINUE, cbxExpectHeader.isSelected());
//            appendMessage("Uploading " + targetFile.getName() + " to " + targetURL);
//        	File targetFile = new File("/tmp/,b.zip");
    		ByteArrayPartSource partSource = new ByteArrayPartSource("data.zipped", compressed);		
            Part[] parts = {
        			new FilePart("data.zipped", partSource),
        			new StringPart("path", this.getRoot()),
        			new StringPart("txId", txId),
//        			new StringPart("bundleName", this.name)//, "ISO-8859-1")
            };
            postMethod.setRequestEntity(new MultipartRequestEntity(parts, postMethod.getParams()));
            HttpClient client = new HttpClient();
            client.getHttpConnectionManager().getParams().setConnectionTimeout(30 * 1000);
            int statusCode = client.executeMethod(postMethod);

            if (statusCode != HttpStatus.SC_OK)
			{
				throw new FipException("Unknown response from server: " + statusCode + ": " + HttpStatus.getStatusText(statusCode));
			}
			else
			{
//				logger.info("Updates completed");
			}
	    } catch (Exception ex) {
	    	logger.info("ERROR: " + ex.getClass().getName() + " "+ ex.getMessage());
	        ex.printStackTrace();
	        
	        FipException exception = new FipException(ex.toString());
	        exception.setStackTrace(ex.getStackTrace());
	        throw exception;
	    } finally {
	    	postMethod.releaseConnection();
	    }
	}

	@Override
	public void abortTransaction(String txId)
	{
		// TODO Auto-generated method stub

	}
}
