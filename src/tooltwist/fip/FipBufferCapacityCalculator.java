package tooltwist.fip;

import tooltwist.fip.FipBatchOfUpdates.BufferStatus;

public class FipBufferCapacityCalculator
{
	private int spaceUsed = 0;
	
	public void resetCounter()
	{
		spaceUsed = spaceRequiredAtStartOfBuffer();
	}
	
	public BufferStatus willItFit(long requiredSpaceInBuffer)
	{
		
		if (requiredSpaceInBuffer >= FipBatchOfUpdates.BUFFER_LENGTH)
			return BufferStatus.IMPOSSIBLE_TO_SEND_BIGGER_THAN_BUFFER;
		
		long newLength = spaceUsed + requiredSpaceInBuffer + 1; 
		if (newLength < FipBatchOfUpdates.PREFERRED_MAX_TRANSMISSION)
		{
			// Fits within the preferred size, and there may be room for more.
			spaceUsed += requiredSpaceInBuffer;
			return BufferStatus.WILL_FIT;
		}
		else
		{
			// It won't fit in the preferred size.
			if (spaceUsed < FipBatchOfUpdates.SMALL_ENOUGH_TO_PUT_BIG_FILE_ON_TOP && newLength < FipBatchOfUpdates.BUFFER_LENGTH)
			{
				// Larger than the preferred size, but not by much. This will be the last one in this transfer.
				spaceUsed += requiredSpaceInBuffer;
				return BufferStatus.WILL_FIT;
			}
			else
			{
				// A big too big. Send it in another transfer.
				return BufferStatus.WILL_NOT_FIT;
			}
		}
	}
	
	public int getSpaceUsed()
	{
		return spaceUsed + 1; // One byte for commit
	}

	public int spaceRequiredAtStartOfBuffer()
	{
		return 4; // MAGIC_START_OF_TRANSFER_FILE, VERSION_MAJOR_NUMBER, VERSION_MINOR_NUMBER, MAGIC_END_OF_TRANSFER_FILE
	}

	public long spaceRequiredForUpdate(String relativePath, long fileLen)
	{
		long spaceRequired = 1; // OP_INSTALL_FILE
		spaceRequired += 4 + relativePath.length(); // path = MAGIC_BEFORE_STRING, string length (2 bytes), the string, MAGIC_AFTER_STRING
		spaceRequired += 4; // file length (4 bytes)
		spaceRequired += 1; // MAGIC_BEFORE_FILE_CONTENTS
		spaceRequired += fileLen; // File contents
		spaceRequired += 1; // MAGIC_AFTER_FILE_CONTENTS
//logger.info("Guessed size of " + relativePath + " is " + spaceRequired);
		return spaceRequired;
	}

	public long spaceRequiredForDelete(String relativePath)
	{
		long spaceRequired = 1; // OP_DELETE_FILE
		spaceRequired += 4 + relativePath.length(); // path = MAGIC_BEFORE_STRING, string length (2 bytes), the string, MAGIC_AFTER_STRING
		spaceRequired += 1; // MAGIC_AFTER_DELETE
		return spaceRequired;
	}

	public long spaceRequiredForCommit()
	{
		return 1; // OP_COMMIT_TRANSACTION
	}

}
