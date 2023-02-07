package today.ihelio.paxos;

import java.util.concurrent.atomic.AtomicReference;

public class PaxosServerImpl implements PaxosServer {
	private final AtomicReference<Integer> leader = new AtomicReference<Integer>();
	private final int hostID;
	private final AtomicReference<Integer> proposal = new AtomicReference<Integer>();
	private final int[] indexArray = new int[2000];
	private final int[] valueArray = new int[2000];
	private final AtomicReference<Integer> firstUnchosenIndex = new AtomicReference<>();
	private final AtomicReference<Boolean> noMoreUnaccepted = new AtomicReference<>();
	
	public PaxosServerImpl (int hostID) {
		this.hostID = hostID;
		this.leader.set(hostID);
		firstUnchosenIndex.set(0);
		noMoreUnaccepted.set(true);
		proposal.set(0);
	}
	
	public boolean isLeader() {
		return leader.get() == this.hostID;
	}
	
	@Override
	public int getHostID () {
		return hostID;
	}
	
	@Override
	public void updateLeadership (int leaderID) {
		leader.set(leaderID);
	}
	@Override
	public int getLeaderID() {
		return leader.get();
	}
	
}
