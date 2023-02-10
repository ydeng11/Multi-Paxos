package today.ihelio.paxos;


import com.google.common.base.Stopwatch;
import java.util.TreeSet;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import today.ihelio.paxos.utility.AbstractHost;
import today.ihelio.paxos.utility.Leader;

@Singleton
public class LeaderProvider implements Provider<Leader> {
	private final Logger logger = LoggerFactory.getLogger(LeaderProvider.class);
	private final Stopwatch stopwatch = Stopwatch.createUnstarted();
	private final TreeSet<AbstractHost> serverSet = new TreeSet<>((AbstractHost o1, AbstractHost o2) -> o1.getHostID() - o2.getHostID());
	private final AtomicReference<Long> lastSeenTime = new AtomicReference<>();
	private final AtomicReference<Leader> leader;
	private final AbstractHost localHost;

	public LeaderProvider(AbstractHost host) {
		this.localHost = host;
		this.stopwatch.start();
		this.lastSeenTime.set(System.currentTimeMillis());
		this.leader = new AtomicReference<>(Leader.of(host));
		this.serverSet.add(host);
	}

	public Leader getLeader() {
		return leader.get();
	}

	@Override public Leader get() {
		return leader.get();
	}

	public void processHeartbeat(AbstractHost host) {
		serverSet.add(host);
//		update the largest ID the host has ever seen since it should be the leader
		if (serverSet.last().equals(host)) {
			lastSeenTime.getAndSet(System.currentTimeMillis());
		}
		if (System.currentTimeMillis() - lastSeenTime.get() > 2000 && !serverSet.last().equals(this.localHost)) {
			serverSet.pollLast();
			lastSeenTime.getAndSet(System.currentTimeMillis());
		}
		AbstractHost leaderHost = serverSet.last();
		this.leader.getAndUpdate((v) -> {
			if (v.getHostID() != leaderHost.getHostID() && stopwatch.elapsed(TimeUnit.MILLISECONDS) > 500) {
				restartStopwatch();
				return Leader.of(leaderHost);
			}
			return v;
		}
		);
		logger.info(this.toString());
	}
	
	private void restartStopwatch() {
		stopwatch.reset();
		stopwatch.start();
	}

	public String toString() {
		return "Host ID: " + localHost.getHostID() + " and its leader is: " + leader.get().getHostID();
	}
}
