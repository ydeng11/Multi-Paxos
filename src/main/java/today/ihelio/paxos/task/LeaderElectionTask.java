package today.ihelio.paxos.task;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import today.ihelio.paxos.PaxosServer;
import today.ihelio.paxos.utility.AbstractHost;
import today.ihelio.paxos.utility.HostPorts;
import today.ihelio.paxoscomponents.HeartbeatRequest;
import today.ihelio.paxoscomponents.HeartbeatResponse;
import today.ihelio.paxoscomponents.PaxosServerServiceGrpc;

import static java.util.concurrent.TimeUnit.SECONDS;

public class LeaderElectionTask implements Runnable {
  private final static Logger logger = LoggerFactory.getLogger(LeaderElectionTask.class);
  private final HostPorts hostPorts;
  private final PaxosServer paxosServer;
  private final Map<Integer, PaxosServerServiceGrpc.PaxosServerServiceBlockingStub>
      blockingStubForPeers;
  @Named("LocalHost")
  private final AbstractHost localHost;

  @Inject
  public LeaderElectionTask(HostPorts hostPorts, PaxosServer paxosServer,
      Map<Integer, PaxosServerServiceGrpc.PaxosServerServiceBlockingStub> blockingStubForPeers,
      AbstractHost localHost) {
    this.hostPorts = hostPorts;
    this.paxosServer = paxosServer;
    this.blockingStubForPeers = blockingStubForPeers;
    this.localHost = localHost;
  }

  @Override public void run() {
    while (true) {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      for (int peerPort: hostPorts.ports()) {
        if (peerPort == localHost.getPort()) {
          continue;
        }
        HeartbeatRequest request = HeartbeatRequest.newBuilder().setHostId(paxosServer.getHostID()).build();
        HeartbeatResponse response = HeartbeatResponse.getDefaultInstance();
        try {
          response = blockingStubForPeers.get(peerPort).withDeadlineAfter(5, SECONDS).sendHeartBeat(request);
          logger.info(response.toString());
        } catch (Exception e) {
          logger.error("request failed " + e.getMessage());
        }
      }
    }
  }
}
