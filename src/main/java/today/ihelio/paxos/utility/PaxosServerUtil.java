package today.ihelio.paxos.utility;

public class PaxosServerUtil {
	public static long getProcessID() {
		return ProcessHandle.current().pid();
	}
}
