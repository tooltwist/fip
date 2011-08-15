package tooltwist.fip;

import java.util.regex.Pattern;

public class FipRule_ignore extends FipRule
{
	private Pattern pattern;

	public FipRule_ignore(String pathPattern)
	{
		this.pattern = Pattern.compile(pathPattern);
	}

	@Override
	public void setRuleParametersForFile(FipRuleParameter param)
	{
		String relativePath = param.getSourceRelativePath();
		if (pattern.matcher(relativePath).matches())
			param.setOp(Op.IGNORE);
	}
}
