package today.ihelio.paxos.task;

import com.google.common.base.Stopwatch;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import today.ihelio.paxos.PaxosServer;
import today.ihelio.paxoscomponents.AcceptorResponse;
import today.ihelio.paxoscomponents.DataInsertionRequest;
import today.ihelio.paxoscomponents.PrepareResponse;
import today.ihelio.paxoscomponents.Proposal;

@Singleton
public class PaxosConcensusTask implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(PaxosConcensusTask.class);
  private final PaxosServer paxosServer;
  private final AtomicReference<Proposal> nextProposal;
  private final Stopwatch stopwatch = Stopwatch.createUnstarted();

  @Inject
  public PaxosConcensusTask(PaxosServer paxosServer) {
    this.paxosServer = paxosServer;
    this.nextProposal = new AtomicReference<>();
    this.stopwatch.start();
  }

  @Override public void run() {
    while (true) {
      while (paxosServer.isLeader()) {
        try {
          Thread.sleep(3000);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        while (paxosServer.hasRequest() || nextProposal.get() != null) {
          Proposal proposalMsg;
          DataInsertionRequest dataInsertionRequest = null;
          if (nextProposal.get() != null) {
            proposalMsg = nextProposal.getAndSet(null);
          } else {
            dataInsertionRequest = paxosServer.pollClientRequest();
            proposalMsg = makeProposal(dataInsertionRequest);
          }
          logger.info("replicate: proposal number " + proposalMsg.getProposalNumber() + " proposal value " + proposalMsg.getValue());
          if (!paxosServer.isMostUnaccepted()) {
            Set<Future<PrepareResponse>> responseSets = paxosServer.makeProposalMsg(proposalMsg);
            Proposal returnedProposal =
                paxosServer.processProposalResponse(responseSets, proposalMsg);
            // the returnedProposal from acceptor varies from the proposal sent out
            if (!returnedProposal.equals(proposalMsg)) {
              // if the returned Proposal has new values, we should use the returned values to make proposal
              // and put the request back to the request pool
              if (dataInsertionRequest != null
                  && returnedProposal.getValue() != proposalMsg.getValue()) {
                paxosServer.addClientRequest(dataInsertionRequest);
              }
              nextProposal.getAndSet(returnedProposal);
              break;
            }
          }
          logger.info("send accept msg: proposal number " + proposalMsg.getProposalNumber() + " proposal value " + proposalMsg.getValue());
          Set<Future<AcceptorResponse>> acceptResponseSets =
              paxosServer.sendAcceptMsg(proposalMsg);
          if (paxosServer.processAcceptMsgResponse(acceptResponseSets)) {
            //logger.info("mark chosen");
            paxosServer.setChosen(proposalMsg.getIndex(), proposalMsg.getValue());
            //logger.info("firstUnchosen: " + paxosServer.getFirstUnchosenIndex());
            //logger.info("hash: " + paxosServer.toString());
            try {
              paxosServer.processSuccessMsgResponse(acceptResponseSets);
            } catch (ExecutionException e) {
              throw new RuntimeException(e);
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
          } else {
            nextProposal.getAndSet(proposalMsg);
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
}
