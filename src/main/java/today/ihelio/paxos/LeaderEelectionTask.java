package today.ihelio.paxos;

import java.util.List;

public class LeaderEelectionTask implements Runnable {
	private final String hostId;
	private final List<String> otherHosts;
	private final String leaderServer;
	
	public LeaderEelectionTask (String hostId, List<String> otherHosts, String leaderServer) {
		this.hostId = hostId;
		this.otherHosts = otherHosts;
		this.leaderServer = leaderServer;
	}
	
	
	@Override
	public void run () {
		for (String otherHost : otherHosts) {
		}
	}
}
