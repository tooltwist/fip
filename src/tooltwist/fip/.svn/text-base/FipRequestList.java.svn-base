package tooltwist.fip;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Vector;

/**
 * This class is used to specify a list of files that the client process would
 * like to download from the server.
 * 
 * Note that this list not intended to include every file in the transaction. The FipBufferCapacityCalculator
 * class is used to work out how many files can be included in each transfer, and should be used
 * to ensure that this list does not include more files than can be included in a single transfer.
 * 
 * 
 * @author philipcallender
 *
 */
public class FipRequestList
{
	private Vector<FipRequest> list = new Vector<FipRequest>();

	public void clear()
	{
		list.clear();
	}

	public void addRequestForInstall(String sourceRelativePath, String destinationRelativePath)
	{
		FipRequest request = FipRequest.newUpdateRequest(sourceRelativePath, destinationRelativePath);
		list.add(request);
	}

	public void addRequestForDelete(String destinationRelativePath)
	{
		FipRequest request = FipRequest.newDeleteRequest(destinationRelativePath);
		list.add(request);
	}

	public void addRequestForEndOfTransactionMarker()
	{
		FipRequest request = FipRequest.newEndOfTransactionMarkerRequest();
		list.add(request);
	}

	public void addRequestForCommit()
	{
		FipRequest request = FipRequest.newCommitRequest();
		list.add(request);
	}

	public void addRequestForAbort()
	{
		FipRequest request = FipRequest.newAbortRequest();
		list.add(request);
	}

	public Iterable<FipRequest> list()
	{
		return list;
	}

	public int size()
	{
		return list.size();
	}
	
	public String serialize()
	{
		// Write them out
		StringBuffer buf = new StringBuffer();
		for (FipRequest request : list)
		{
			// Note that the path must be at the end, because it might contain the separator character.
			String line = request.serialize();
			buf.append(line);
		}
		return buf.toString();
	}

	public static FipRequestList deserialize(BufferedReader in) throws IOException, FipException
	{
		FipRequestList newList = new FipRequestList();
		for ( ; ; )
		{
			String line = in.readLine();
			if (line == null)
				break;
			FipRequest request = FipRequest.deSerialize(line);
			newList.list.add(request);
		}
		return newList;
	}

}
