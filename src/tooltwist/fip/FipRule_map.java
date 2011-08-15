package tooltwist.fip;

public class FipRule_map extends FipRule
{
	private String originalRelativePath;
	private String newRelativePath;

	public FipRule_map(String sourceRelativePath, String destinationRelativePath)
	{
		this.originalRelativePath = sourceRelativePath;
		this.newRelativePath = destinationRelativePath;
	}

	@Override
	public void setRuleParametersForFile(FipRuleParameter param)
	{
		String sourceRelativePath = param.getSourceRelativePath();
		if (sourceRelativePath.equals(originalRelativePath))
			param.setDestinationRelativePath(newRelativePath);
	}

}
