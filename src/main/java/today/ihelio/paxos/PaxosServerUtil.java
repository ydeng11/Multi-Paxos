package today.ihelio.paxos;

public class PaxosServerUtil {
	public static long getProcessID() {
		return ProcessHandle.current().pid();
	}
}
