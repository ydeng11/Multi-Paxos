package today.ihelio.paxos;


import com.google.common.base.Stopwatch;
import javax.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import today.ihelio.paxos.utility.Leader;

public class LeaderElectionTask {
	private final Logger logger = LoggerFactory.getLogger(LeaderElectionTask.class);
	private PaxosServer paxosServer;
	private final Stopwatch stopwatch = Stopwatch.createUnstarted();
	private final Stopwatch stopwatchLargestID = Stopwatch.createUnstarted();
	private final int hostID;
	private final AtomicReference<Integer> largestIDSeen = new AtomicReference<>();;
	private final AtomicReference<Long> lastSeen = new AtomicReference<>();
	
	public LeaderElectionTask (PaxosServer paxosServer, int hostID) {
		this.paxosServer = paxosServer;
		this.hostID = hostID;
		this.largestIDSeen.set(hostID);
		this.stopwatch.start();
		this.stopwatchLargestID.start();
		this.lastSeen.set(System.currentTimeMillis());
	}
	
	public void processHeartbeat(int otherHostID) {
//		update the largest ID the host has ever seen since it should be the leader
		if (otherHostID >= largestIDSeen.get()) {
			largestIDSeen.set(otherHostID);
			lastSeen.set(System.currentTimeMillis());
		}
		if (System.currentTimeMillis() - lastSeen.get() > 2000) {
			largestIDSeen.set(hostID);
			lastSeen.set(System.currentTimeMillis());
		}
		int largestID = largestIDSeen.get();
//		update leader ID when a new leader is found, and it is the leader
		if (paxosServer.getLeaderID() != largestID) {
			paxosServer.updateLeadership(largestID);
			restartStopwatch();
		}
		else {
			if (stopwatch.elapsed(TimeUnit.MILLISECONDS) > 500 && largestID == this.hostID && !paxosServer.isLeader()) {
				paxosServer.updateLeadership(largestID);
				restartStopwatch();
			}
		}
	}
	
	private void restartStopwatch() {
		stopwatch.reset();
		stopwatch.start();
	}
	
	public void resetLargestSeen() {
		largestIDSeen.set(null);
	}
	
	public String toString() {
		return "Host ID: " + hostID + " and its leader is: " + paxosServer.getLeaderID();
	}
}
