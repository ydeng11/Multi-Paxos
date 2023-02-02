package today.ihelio.paxos;


import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;


public class LeaderElectionTask {
	private final Logger logger = LoggerFactory.getLogger(LeaderElectionTask.class);
	private PaxosServer paxosServer;
	private final Stopwatch stopwatch = Stopwatch.createUnstarted();
	private final int hostID;
	private final AtomicReference<Integer> largestIDSeen = new AtomicReference<>();;
	
	public LeaderElectionTask (PaxosServer paxosServer, int hostID) {
		this.paxosServer = paxosServer;
		this.hostID = hostID;
		this.largestIDSeen.set(hostID);
		this.stopwatch.start();
	}
	
	public void processHeartbeat(int otherHostID) {
		if (otherHostID > largestIDSeen.get()) {
			largestIDSeen.set(otherHostID);
		}
		int largestID = largestIDSeen.get();
		if (hostID != largestID && paxosServer.isLeader()) {
			paxosServer.updateLeadership(false);
			restartStopwatch();
		} else {
			if (stopwatch.elapsed(TimeUnit.MILLISECONDS) > 500 && largestID == hostID && !paxosServer.isLeader()) {
				paxosServer.updateLeadership(true);
				restartStopwatch();
			}
		}
		logger.info(this.toString());
	}
	
	private void restartStopwatch() {
		stopwatch.reset();
		stopwatch.start();
	}
	
	public String toString() {
		return "Host ID: " + hostID + " is leader: " + paxosServer.isLeader();
	}
}
