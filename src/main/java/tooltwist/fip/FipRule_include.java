package tooltwist.fip;

import java.util.regex.Pattern;

public class FipRule_include extends FipRule
{
	private Pattern pattern;

	public FipRule_include(String pathPattern)
	{
		this.pattern = Pattern.compile(pathPattern);
	}

	@Override
	public void setRuleParametersForFile(FipRuleParameter param)
	{
		String relativePath = param.getSourceRelativePath();
		if (pattern.matcher(relativePath).matches())
			param.setOp(Op.INCLUDE);
	}
	
	public String toString() {
		return this.getClass().getCanonicalName() + ": " + pattern;
	}
}
