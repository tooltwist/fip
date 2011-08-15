package tooltwist.fip;

import tooltwist.fip.FipRule.Op;

public interface FipRuleParameter
{
	public String getSourceRelativePath();

	public String getDestinationRelativePath();

	public void setDestinationRelativePath(String destinationRelativePath);

	public Op getOp();

	public void setOp(Op op);

	
}
