package tooltwist.fip;

public abstract class FipRule
{
	enum Op { INCLUDE, EXCLUDE, IGNORE }

	public abstract void setRuleParametersForFile(FipRuleParameter param);
}
