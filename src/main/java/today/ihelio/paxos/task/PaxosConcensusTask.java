package today.ihelio.paxos.task;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import today.ihelio.paxos.PaxosServer;
import today.ihelio.paxos.utility.AbstractHost;
import today.ihelio.paxos.utility.Hosts;
import today.ihelio.paxos.utility.StubFactory;
import today.ihelio.paxoscomponents.DataInsertionRequest;
import today.ihelio.paxoscomponents.PrepareRequest;
import today.ihelio.paxoscomponents.PrepareResponse;
import today.ihelio.paxoscomponents.Proposal;

import static java.util.concurrent.TimeUnit.SECONDS;

@Singleton
public class PaxosConcensusTask implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(PaxosConcensusTask.class);
  private final PaxosServer paxosServer;
  private final Hosts hosts;
  private final AbstractHost localHost;
  private final StubFactory stubFactory;
  private final AtomicInteger localProposalNumber;
  private final ExecutorService executorService;
  private final Set<Future<PrepareResponse>> taskSet;
  private final AtomicInteger proposalQuorumCount;
  private final AtomicInteger unacceptedAcceptorCount;

  @Inject
  public PaxosConcensusTask(PaxosServer paxosServer, Hosts hosts, StubFactory stubFactory,
      @Named("LocalHost") AbstractHost localHost) {
    this.paxosServer = paxosServer;
    this.hosts = hosts;
    this.stubFactory = stubFactory;
    this.localHost = localHost;
    this.localProposalNumber = new AtomicInteger(0);
    this.executorService = Executors.newCachedThreadPool();
    this.taskSet = new HashSet<>();
    this.proposalQuorumCount = new AtomicInteger(0);
    this.unacceptedAcceptorCount = new AtomicInteger(0);
  }

  @Override public void run() {
    while (paxosServer.isLeader() && paxosServer.hasRequest()) {
      taskSet.clear();
      if (unacceptedAcceptorCount.get() > hosts.hosts().size() / 2) {
      //  only send accept message
        // respond accept response
      } else {
      //  send proposal
      //    response proposalResponse
      //        -> proposalResponse create the accept message
          //  send accept message
          //    response accept response
      }
      DataInsertionRequest dataInsertionRequest = paxosServer.getClientRequest();
      Proposal proposal = Proposal.newBuilder().setProposalNumber(localProposalNumber.get())
          .setHostID(localHost.getHostID())
          .setIndex(paxosServer.getFirstUnchosenIndex())
          .setValue(dataInsertionRequest.getValue())
          .build();

      PrepareRequest request = PrepareRequest.newBuilder().setProposal(proposal).build();
      for (AbstractHost peer: hosts.hosts()) {
        if (peer.equals(localHost)) {
          continue;
        }
        Future<PrepareResponse> futureResponse = executorService.submit(new Callable<PrepareResponse>() {
          @Override public PrepareResponse call() throws Exception {
            try {
              PrepareResponse response = stubFactory.getBlockingStub(peer)
                  .withDeadlineAfter(5, SECONDS)
                  .makeProposalMsg(request);
              return response;
            } catch (Exception e) {
              logger.error("request failed " + e.getMessage());
              return PrepareResponse.getDefaultInstance();
            }
          }
        });
        taskSet.add(futureResponse);
      }
      for (Future<PrepareResponse> prepareResponseFuture : taskSet) {
        try {
          PrepareResponse response = prepareResponseFuture.get();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        } catch (ExecutionException e) {
          throw new RuntimeException(e);
        }


      }
    }
  }
}
