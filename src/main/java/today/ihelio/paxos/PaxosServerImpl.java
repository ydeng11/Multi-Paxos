package today.ihelio.paxos;

import java.util.concurrent.atomic.AtomicReference;

public class PaxosServerImpl implements PaxosServer {
	private final AtomicReference<Boolean> leader = new AtomicReference<Boolean>();
	private final int hostID;
	
	public PaxosServerImpl (int hostID) {
		this.hostID = hostID;
		this.leader.set(false);
	}
	
	public boolean isLeader() {
		return leader.get();
	}
	
	@Override
	public int getHostID () {
		return hostID;
	}
	
	@Override
	public void updateLeadership (boolean isLeader) {
		leader.set(isLeader);
	}
	
}
