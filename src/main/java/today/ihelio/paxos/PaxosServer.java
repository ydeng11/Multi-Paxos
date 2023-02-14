package today.ihelio.paxos;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.name.Named;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import today.ihelio.paxos.utility.AbstractHost;
import today.ihelio.paxos.utility.Hosts;
import today.ihelio.paxos.utility.Leader;
import today.ihelio.paxos.utility.StubFactory;
import today.ihelio.paxoscomponents.AcceptRequest;
import today.ihelio.paxoscomponents.AcceptorResponse;
import today.ihelio.paxoscomponents.DataInsertionRequest;
import today.ihelio.paxoscomponents.PrepareRequest;
import today.ihelio.paxoscomponents.PrepareResponse;
import today.ihelio.paxoscomponents.Proposal;
import today.ihelio.paxoscomponents.SuccessRequest;

import static java.util.concurrent.TimeUnit.SECONDS;

@Singleton
public class PaxosServer {
	private static final Logger logger = LoggerFactory.getLogger(PaxosServer.class);
	private final Provider<Leader> leader;
	private final AbstractHost localHost;
	private final boolean[] choosenArray = new boolean[2000];
	private final String[] valueArray = new String[2000];
	private final AtomicReference<Integer> firstUnchosenIndex = new AtomicReference<>();
	private final AtomicReference<Boolean> noMoreUnaccepted = new AtomicReference<>();
	private final Queue<DataInsertionRequest> eventsQueue = new ConcurrentLinkedQueue<>();
	private final Hosts hosts;
	private final ExecutorService executorService;
	private final StubFactory stubFactory;
	private final AtomicInteger localProposalNumber;
	private final AtomicInteger proposalQuorumCount;
	private final Map<Integer, Boolean> unacceptedStatusPeers = new ConcurrentHashMap<>();
	private final AtomicInteger unacceptedAcceptorCount;

	private final AtomicInteger acceptedNotChosen = new AtomicInteger(0);

	/**
	 * Set a global proposal since we should have only one leader thus eventually
	 * the proposal should be consistent across the servers
	 */
	private final AtomicReference<Integer> proposal = new AtomicReference<Integer>();

	@Inject
	public PaxosServer(@Named("LocalHost") AbstractHost host, Hosts hosts,
			StubFactory stubFactory, Provider<Leader> leader) {
		this.localHost = host;
		this.leader = leader;
		this.firstUnchosenIndex.set(0);
		this.noMoreUnaccepted.set(true);
		this.localProposalNumber = new AtomicInteger(0);
		this.proposalQuorumCount = new AtomicInteger(0);
		this.proposal.set(0);
		this.hosts = hosts;
		this.stubFactory = stubFactory;
		this.executorService = Executors.newSingleThreadExecutor();
		this.unacceptedAcceptorCount = new AtomicInteger(0);

	}
	
	public boolean isLeader() {
		return leader.get().getHostID() == this.localHost.getHostID();
	}
	
	public int getHostID () {
		return this.localHost.getHostID();
	}
	public int getLeaderID() {
		return leader.get().getHostID();
	}

	public void addClientRequest(DataInsertionRequest dataInsertionRequest) {
		eventsQueue.add(dataInsertionRequest);
	}

	@Nullable
	public DataInsertionRequest getClientRequest() {
		return eventsQueue.poll();
	}

	public int getFirstUnchosenIndex() {
		return firstUnchosenIndex.get();
	}

	public boolean hasRequest() {
		return !eventsQueue.isEmpty();
	}

	public Set<Future<PrepareResponse>> sendProposalMsg(Proposal proposal) {
		Set<Future<PrepareResponse>> taskSet = new HashSet<>();

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
		return taskSet;
	}

	private Proposal processProposalResponse(Set<Future<PrepareResponse>> taskSet, Proposal proposalMsg) {
		List<Proposal> responseProposalsFromAcceptor = taskSet.stream().map((v) -> {
			try {
				return v.get().getProposal();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			} catch (ExecutionException e) {
				throw new RuntimeException(e);
			}
		}).collect(
				ImmutableList.toImmutableList());
		int uniqueValues = (int) responseProposalsFromAcceptor.stream()
				.map((v) -> v.getValue()).collect(ImmutableSet.toImmutableSet())
				.stream().count();
		if (uniqueValues > 2) {
			throw new RuntimeException("received more than 2 different accepted values from "
					+ "acceptors");
		}

		proposalQuorumCount.getAndSet(0);
		for (Proposal proposal : responseProposalsFromAcceptor) {
			if (proposal.equals(proposalMsg)) {
				proposalQuorumCount.getAndIncrement();
			} else {
				if (proposal.getProposalNumber() > proposalMsg.getProposalNumber()) {
					localProposalNumber.getAndAdd(hosts.hosts().size());
					Proposal newProposal = proposalMsg.toBuilder().setProposalNumber(localProposalNumber.get()).build();
					return newProposal;
				} else {
					if (proposal.getValue() != proposalMsg.getValue()) {
						Proposal newProposal = proposalMsg.toBuilder().setValue(proposal.getValue()).build();
						return newProposal;
					}
				}
			}
		}
		if (proposalQuorumCount.get() > hosts.hosts().size() / 2 + 1) {
			return proposalMsg;
		} else {
			localProposalNumber.getAndIncrement();
			return proposalMsg.toBuilder().setProposalNumber(localProposalNumber.get()).build();
		}
	}

	/**
	 * When receiving proposal, we use a global proposal for the whole log to block old proposals
	 * But each proposal should still have one value for a particular place
	 */
	public Proposal processProposalRequest(Proposal proposal) {
		Proposal.Builder proposalBuilder = proposal.toBuilder();
		if (proposal.getProposalNumber() < this.proposal.get()) {
			proposalBuilder.setProposalNumber(this.proposal.get());
		}
		if (valueArray[proposal.getIndex()] != null) {
			proposalBuilder.setValue(valueArray[proposal.getIndex()]);
		}
		return proposalBuilder.build();
	}

	private Set<Future<AcceptorResponse>> sendAcceptMsg(Proposal proposal) {
		Set<Future<AcceptorResponse>> taskSet = new HashSet<>();

		AcceptRequest request = AcceptRequest.newBuilder().setProposalNumber(proposal.getProposalNumber())
				.setIndex(proposal.getIndex())
				.setValue(proposal.getValue())
				.setFirstUnchosenIndex(getFirstUnchosenIndex())
				.build();
		for (AbstractHost peer: hosts.hosts()) {
			if (peer.equals(localHost)) {
				continue;
			}
			Future<AcceptorResponse> futureResponse = executorService.submit(new Callable<AcceptorResponse>() {
				@Override public AcceptorResponse call() throws Exception {
					try {
						AcceptorResponse response = stubFactory.getBlockingStub(peer)
								.withDeadlineAfter(5, SECONDS)
								.makeAcceptMsg(request);
						return response;
					} catch (Exception e) {
						logger.error("request failed " + e.getMessage());
						return AcceptorResponse.getDefaultInstance();
					}
				}
			});
			taskSet.add(futureResponse);
		}
		return taskSet;
	}

	private void processAcceptResponse() {

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
		if (acceptRequest.getProposalNumber() != proposal.get()) {
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

	public int getLocalProposalNumber() {
		return localProposalNumber.get();
	}
}
