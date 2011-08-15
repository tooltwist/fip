package tooltwist.fip;


public class FipRule_mapDirectory extends FipRule
{
	private String subPath;
	private String newSubPath;

	public FipRule_mapDirectory(String pathPrefix, String newPathPrefix) throws FipException
	{
		if ( !pathPrefix.endsWith("/") || !newPathPrefix.endsWith("/"))
			throw new FipException("Path prefixes must end with '/'");
		this.subPath = pathPrefix;
		this.newSubPath = newPathPrefix;
	}

	@Override
	public void setRuleParametersForFile(FipRuleParameter param)
	{
		String sourceRelativePath = param.getSourceRelativePath();
		int pos = sourceRelativePath.indexOf(subPath);
		if (pos == 0)
		{
			String after = sourceRelativePath.substring(pos + subPath.length());
			String newRelativePath = newSubPath + after;
			param.setDestinationRelativePath(newRelativePath);
		}
	}
}
