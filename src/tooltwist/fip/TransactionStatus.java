/**
 * 
 */
package tooltwist.fip;

public enum TransactionStatus {
	PREPARING(		Fip.PREFIX + "prepare-",	"Preparing"),
	READY_TO_COMMIT(Fip.PREFIX + "ready-",		"Ready for commit"),
	ROLLBACK(		Fip.PREFIX + "rollback-",	"Committed, available for rollback"),
	PART_COMMITTED(	Fip.PREFIX + "incremental-","Partly committed (incremental)"),
	ABORTED(		Fip.PREFIX + "aborted-",	"Aborted"),
	EXPIRED(		Fip.PREFIX + "expired-",	"Expired");

	private String prefix;
	private String description;
	private TransactionStatus(String prefix, String description) { this.prefix = prefix; this.description = description; }
	public String getPrefix() { return prefix; }
	public String getDescription() { return description; }
}