package today.ihelio.paxos.task;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import today.ihelio.paxos.PaxosServer;
import today.ihelio.paxos.utility.AbstractHost;
import today.ihelio.paxos.utility.Hosts;
import today.ihelio.paxos.utility.StubFactory;
import today.ihelio.paxoscomponents.AcceptRequest;
import today.ihelio.paxoscomponents.AcceptorResponse;
import today.ihelio.paxoscomponents.DataInsertionRequest;
import today.ihelio.paxoscomponents.PrepareRequest;
import today.ihelio.paxoscomponents.PrepareResponse;
import today.ihelio.paxoscomponents.Proposal;

import static java.util.concurrent.TimeUnit.SECONDS;

@Singleton
public class PaxosConcensusTask implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(PaxosConcensusTask.class);
  private final PaxosServer paxosServer;
  private final AtomicReference<Proposal> nextProposal;

  @Inject
  public PaxosConcensusTask(PaxosServer paxosServer) {
    this.paxosServer = paxosServer;
    this.nextProposal = new AtomicReference<>();
  }

  @Override public void run() {
    while (paxosServer.isLeader()) {
      while (paxosServer.hasRequest() || nextProposal.get() != null) {
        Proposal proposalMsg;
        DataInsertionRequest dataInsertionRequest = null;
        if (nextProposal.get() != null) {
          proposalMsg = nextProposal.getAndSet(null);
        } else {
          dataInsertionRequest = paxosServer.getClientRequest();
          proposalMsg = makeProposal(dataInsertionRequest);
        }
        if (unacceptedAcceptorCount.get() >= hosts.hosts().size() / 2 + 1) {
          //  only send accept message
          // respond accept response
        } else {
          //  send proposal msg
          Set<Future<PrepareResponse>> taskSet = paxosServer.sendProposalMsg(proposalMsg);
          // process the response from acceptors regarding this proposal
          Proposal responseProposal = processProposalResponse(taskSet, proposalMsg);
          // the responseProposal is the same as the proposalMsg which means most acceptors
          // could accept it
          if (responseProposal.equals(proposalMsg)) {
            // start accept
            //AcceptRequest acceptRequest =
          } else {
            //  redo proposal
            if (dataInsertionRequest != null && dataInsertionRequest.getValue() != responseProposal.getValue()) {
              paxosServer.addClientRequest(dataInsertionRequest);
            }
            nextProposal.getAndUpdate((v) -> {
              if (v != null) {
                throw new RuntimeException("nextProposal must be null before setting a new waiting proposal");
              }
              return responseProposal;
            });
          }
        }
      }
    }

  }

  private Proposal makeProposal(DataInsertionRequest dataInsertionRequest) {
    Proposal proposal = Proposal.newBuilder().setProposalNumber(paxosServer.getLocalProposalNumber())
        .setHostID(paxosServer.getHostID())
        .setIndex(paxosServer.getFirstUnchosenIndex())
        .setValue(dataInsertionRequest.getValue())
        .build();
    return proposal;
  }


  private int generateRandom() {
    Random rand = new Random();
    return rand.nextInt(20, 100);
  }

}
