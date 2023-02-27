package today.ihelio.paxos.task;

import javax.inject.Inject;
import javax.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import today.ihelio.paxos.PaxosServer;
import today.ihelio.paxos.utility.AbstractHost;
import today.ihelio.paxos.utility.Hosts;
import today.ihelio.paxos.utility.StubFactory;
import today.ihelio.paxoscomponents.HeartbeatRequest;
import today.ihelio.paxoscomponents.HeartbeatResponse;

import static java.util.concurrent.TimeUnit.SECONDS;

public class LeaderElectionTask implements Runnable {
  private final static Logger logger = LoggerFactory.getLogger(LeaderElectionTask.class);
  private final Hosts hosts;
  private final PaxosServer paxosServer;
  private final StubFactory stubFactory;
  private final AbstractHost localHost;

  @Inject
  public LeaderElectionTask(Hosts hosts, PaxosServer paxosServer,
      StubFactory stubFactory,
      @Named("LocalHost") AbstractHost localHost) {
    this.hosts = hosts;
    this.paxosServer = paxosServer;
    this.stubFactory = stubFactory;
    this.localHost = localHost;
  }

  @Override public void run() {
    while (true) {
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      for (AbstractHost peer: hosts.hosts()) {
        if (peer.equals(localHost)) {
          continue;
        }
        HeartbeatRequest request = HeartbeatRequest.newBuilder()
            .setHostId(localHost.getHostID())
            .setAddress(localHost.getAddress())
            .setPort(localHost.getPort())
            .build();
        HeartbeatResponse response = HeartbeatResponse.getDefaultInstance();
        try {
          response = stubFactory.getBlockingStub(peer).withDeadlineAfter(5, SECONDS).sendHeartBeat(request);
        } catch (Exception e) {
          logger.error("request failed " + e.getMessage());
        }
      }
    }
  }
}
