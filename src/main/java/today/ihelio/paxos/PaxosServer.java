package today.ihelio.paxos;

import com.google.inject.name.Named;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import today.ihelio.paxos.utility.AbstractHost;
import today.ihelio.paxos.utility.HostPorts;
import today.ihelio.paxos.utility.Leader;
import today.ihelio.paxoscomponents.AcceptRequest;
import today.ihelio.paxoscomponents.AcceptorResponse;
import today.ihelio.paxoscomponents.DataInsertionRequest;
import today.ihelio.paxoscomponents.Proposal;
import today.ihelio.paxoscomponents.SuccessRequest;

@Singleton
public class PaxosServer {
	private final Provider<Leader> leader;
	private final AbstractHost host;
	private final boolean[] choosenArray = new boolean[2000];
	private final String[] valueArray = new String[2000];
	private final AtomicReference<Integer> firstUnchosenIndex = new AtomicReference<>();
	private final AtomicReference<Boolean> noMoreUnaccepted = new AtomicReference<>();
	private final Queue<DataInsertionRequest> eventsQueue = new ConcurrentLinkedQueue<>();
	private final HostPorts hostPorts;
	private final Map<Integer, Boolean> unacceptedStatusPeers = new ConcurrentHashMap<>();
	private final AtomicInteger acceptedNotChosen = new AtomicInteger(0);

	/**
	 * Set a global proposal since we should have only one leader thus eventually
	 * the proposal should be consistent across the servers
	 */
	private final AtomicReference<Integer> proposal = new AtomicReference<Integer>();

	@Inject
	public PaxosServer(@Named("LoaclHost") AbstractHost host, HostPorts hostPorts, Provider<Leader> leader) {
		this.host = host;
		this.leader = leader;
		firstUnchosenIndex.set(0);
		noMoreUnaccepted.set(true);
		proposal.set(0);
		this.hostPorts = hostPorts;
	}
	
	public boolean isLeader() {
		return leader.get().getHostID() == this.host.getHostID();
	}
	
	public int getHostID () {
		return this.host.getHostID();
	}
	public int getLeaderID() {
		return leader.get().getHostID();
	}

	public void addClientRequest(DataInsertionRequest dataInsertionRequest) {
		eventsQueue.add(dataInsertionRequest);
	}

	public DataInsertionRequest getClientRequest() {
		return eventsQueue.poll();
	}

	/**
	 * When receiving proposal, we use a global proposal for the whole log to block old proposals
	 * But each proposal should still have one value for a particular place
	 */
	public Proposal processProposal(Proposal proposal) {
		Proposal.Builder proposalBuilder = proposal.toBuilder();
		if (proposal.getProposalNumber() < this.proposal.get()) {
			proposalBuilder.setProposalNumber(this.proposal.get());
		}
		if (valueArray[proposal.getIndex()] != null) {
			proposalBuilder.setValue(valueArray[proposal.getIndex()]);
		}
		return proposalBuilder.build();
	}

	/**
	 * Process the acceptRequest
	 *
	 * Since we are using global proposal thus acceptRequest must carry the same proposal
	 * If the proposal matches, the server should mark all entries up to the firstUnchosenIndex from
	 * Server as chosen. And we need check if the proposed index has any value
	 *
	 * the firstUnchosenIndex need be updated accordingly
	 *
	 * @param acceptRequest
	 * @return
	 */
	public AcceptorResponse processAcceptorRequest(AcceptRequest acceptRequest) {
		if (acceptRequest.getProposal() != proposal.get()) {
			return AcceptorResponse.newBuilder().setHighestProposal(proposal.get())
					.setFirstUnchosenIndex(firstUnchosenIndex.get())
					.setNoUnacceptedValue(noMoreUnaccepted.get())
					.setResponseStatus(false)
					.build();
		}
		for (int i = 0; i < acceptRequest.getFirstUnchosenIndex(); i++) {
			if (choosenArray[i]) {continue;}
			choosenArray[i] = true;
			acceptedNotChosen.getAndDecrement();
		}
		valueArray[acceptRequest.getIndex()] = acceptRequest.getValue();
		acceptedNotChosen.getAndIncrement();
		firstUnchosenIndex.getAndSet(acceptRequest.getIndex());

		return AcceptorResponse.newBuilder().setHighestProposal(proposal.get())
				.setFirstUnchosenIndex(firstUnchosenIndex.get())
				.setNoUnacceptedValue(acceptedNotChosen.get() == 0)
				.setResponseStatus(true).build();
	}

	public AcceptorResponse processSuccessRequest(SuccessRequest successRequest) {
		choosenArray[successRequest.getIndex()] = true;
		acceptedNotChosen.getAndDecrement();
		firstUnchosenIndex.getAndSet(successRequest.getIndex() + 1);
		return AcceptorResponse.newBuilder().setHighestProposal(proposal.get())
				.setFirstUnchosenIndex(firstUnchosenIndex.get())
				.setNoUnacceptedValue(acceptedNotChosen.get() == 0)
				.setResponseStatus(true).build();
	}

	public String toString() {
		return String.valueOf(valueArrayHash());
	}

	private long valueArrayHash() {
		return this.valueArray.hashCode();
	}

}
