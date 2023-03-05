package today.ihelio.paxos;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.name.Named;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import today.ihelio.paxos.annotations.Leader;
import today.ihelio.paxos.utility.AbstractHost;
import today.ihelio.paxos.utility.Hosts;
import today.ihelio.paxos.utility.StubFactory;
import today.ihelio.paxoscomponents.AcceptRequest;
import today.ihelio.paxoscomponents.AcceptorResponse;
import today.ihelio.paxoscomponents.DataInsertionRequest;
import today.ihelio.paxoscomponents.DataInsertionResponse;
import today.ihelio.paxoscomponents.PrepareRequest;
import today.ihelio.paxoscomponents.PrepareResponse;
import today.ihelio.paxoscomponents.Proposal;
import today.ihelio.paxoscomponents.SuccessRequest;

import static java.util.concurrent.TimeUnit.SECONDS;

@Singleton
public class PaxosServer {
	private static final Logger logger = LoggerFactory.getLogger(PaxosServer.class);
	private final Provider<AbstractHost> leader;
	private final AbstractHost localHost;
	private final AtomicReferenceArray<Boolean> chosenArray;
	private final AtomicReferenceArray<String> acceptedValueArray;
	private final AtomicInteger firstUnchosenIndex;
	private final AtomicReference<Boolean> noMoreUnaccepted;
	private final Queue<DataInsertionRequest> eventsQueue;
	private final Hosts hosts;
	private final ExecutorService executorService;
	private final StubFactory stubFactory;
	private final AtomicInteger localProposalNumber;
	private final Map<Integer, Boolean> unacceptedStatusPeers;
	private final AtomicInteger acceptedNotChosen;

	@Inject
	public PaxosServer(@Named("LocalHost") AbstractHost host, Hosts hosts,
			StubFactory stubFactory, @Leader Provider<AbstractHost> leader) {
		this.localHost = host;
		this.leader = leader;
		this.stubFactory = stubFactory;
		this.hosts = hosts;
		this.executorService = Executors.newSingleThreadExecutor();
		this.firstUnchosenIndex = new AtomicInteger(0);
		this.noMoreUnaccepted = new AtomicReference<>(true);
		this.localProposalNumber = new AtomicInteger(0);
		this.acceptedValueArray = new AtomicReferenceArray<>(2000);
		this.chosenArray = new AtomicReferenceArray<>(2000);
		this.acceptedNotChosen = new AtomicInteger(0);
		this.unacceptedStatusPeers = new ConcurrentHashMap<>();
		this.eventsQueue = new ConcurrentLinkedQueue<>();
	}
	
	public boolean isLeader() {
		return getHostID() == getLeaderID();
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
		return eventsQueue.peek();
	}

	@Nullable
	public DataInsertionRequest pollClientRequest() {
		return eventsQueue.poll();
	}

	public int getFirstUnchosenIndex() {
		return firstUnchosenIndex.get();
	}

	public boolean hasRequest() {
		return getRequestSize() > 0;
	}

	public int getRequestSize() {
		return eventsQueue.size();
	}

	public Set<Future<PrepareResponse>> makeProposalMsg(Proposal proposal) {
		Set<Future<PrepareResponse>> taskSet = new HashSet<>();

		PrepareRequest request = PrepareRequest.newBuilder().setProposal(proposal).build();
		for (AbstractHost peer: hosts.hosts()) {
			if (peer.equals(localHost)) {
				continue;
			}
			Future<PrepareResponse> futureResponse = executorService.submit(() -> {
					try {
						PrepareResponse response = stubFactory.getBlockingStub(peer)
								.withDeadlineAfter(5, SECONDS)
								.makeProposalMsg(request);
						return response;
					} catch (Exception e) {
						logger.error("request failed " + e.getMessage());
						return PrepareResponse.getDefaultInstance();
					}
				});
			taskSet.add(futureResponse);
		}
		return taskSet;
	}

	/**
	 * When receiving proposal, we use a global proposal for the whole log to block old proposals
	 * But each proposal should still have one value for a particular place
	 */
	public Proposal processProposalRequest(Proposal proposal) {
		logger.info("host " + localHost.getHostID() + " get proposal number " + proposal.getProposalNumber()
		+ " proposal value " + proposal.getValue());
		Proposal.Builder proposalBuilder = proposal.toBuilder();
		if (proposal.getProposalNumber() < this.localProposalNumber.get()) {
			proposalBuilder.setProposalNumber(this.localProposalNumber.get());
		} else {
			this.localProposalNumber.getAndSet(proposal.getProposalNumber());
		}
		// valueArray is accepted value
		if (acceptedValueArray.get(proposal.getIndex()) != null) {
			proposalBuilder.setValue(acceptedValueArray.get(proposal.getIndex()));
		}
		return proposalBuilder.build();
	}

	public Proposal processProposalResponse(Set<Future<PrepareResponse>> taskSet, Proposal proposalMsg) {
		List<Proposal> responseProposalsFromAcceptor = taskSet.stream().map((v) -> {
			try {
				return v.get().getProposal();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			} catch (ExecutionException e) {
				throw new RuntimeException(e);
			}
		}).collect(ImmutableList.toImmutableList());

		int uniqueValues = (int) responseProposalsFromAcceptor.stream()
				.map((v) -> v.getValue()).collect(ImmutableSet.toImmutableSet())
				.stream().count();

		if (uniqueValues > 2) {
			throw new RuntimeException("received more than 2 different accepted values from "
					+ "acceptors");
		}

		int quorumCount = 0;
		// increment quorum if the returned proposal is as the same as the proposal sent
		// otherwise we should resend proposal with higher proposal number
		// or replace the value from the accepted value in the acceptors
		for (Proposal proposal : responseProposalsFromAcceptor) {
			if (proposal.equals(proposalMsg)) {
				quorumCount += 1;
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
		// if the quorum is larger than the half size, we can return the proposal for next step
		// otherwise we need increment proposal number and retry
		// it determines if we should go ahead sending acceptRequest
		if (quorumCount >= (hosts.hosts().size() - 1) / 2) {
			return proposalMsg;
		} else {
			localProposalNumber.getAndIncrement();
			return proposalMsg.toBuilder().setProposalNumber(localProposalNumber.get()).build();
		}
	}

	public Set<Future<AcceptorResponse>> sendAcceptMsg(Proposal proposal) {
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
			Future<AcceptorResponse> futureResponse = executorService.submit(() -> {
					try {
						AcceptorResponse response = stubFactory.getBlockingStub(peer)
								.withDeadlineAfter(5, SECONDS)
								.makeAcceptMsg(request);
						return response;
					} catch (Exception e) {
						logger.error("request failed " + e.getMessage());
						logger.error("peer addr " + peer.getAddress() + "peer port " + peer.getPort() + "if peer exits "
								+ String.valueOf(stubFactory.getBlockingStub(peer) != null));
						return AcceptorResponse.getDefaultInstance();
					}
				});
			taskSet.add(futureResponse);
		}
		return taskSet;
	}

	public boolean processAcceptMsgResponse(Set<Future<AcceptorResponse>> taskSet) {
		for (Future<AcceptorResponse> acceptorResponseFuture : taskSet) {
			try {
				AcceptorResponse acceptorResponse = acceptorResponseFuture.get();
				unacceptedStatusPeers.put(acceptorResponse.getHostId(), acceptorResponse.getNoUnacceptedValue());
				// should start from proposal again if the response is false
				if (acceptorResponse != null && acceptorResponse.getResponseStatus() != true) {
					logger.info("got false status from " + acceptorResponse.getHostId());
					localProposalNumber.getAndUpdate((v) -> {
						if (v < acceptorResponse.getHighestProposal()) {
							return acceptorResponse.getHighestProposal() + 1;
						}
						return v;
					});
					return false;
				}
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			} catch (ExecutionException e) {
				throw new RuntimeException(e);
			}
		}
		return true;
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
	 */
	public AcceptorResponse processAcceptRequest(AcceptRequest acceptRequest) {
		logger.info("deal with accept request");
		if (acceptRequest.getProposalNumber() != localProposalNumber.get()) {
			return AcceptorResponse.newBuilder().setHighestProposal(localProposalNumber.get())
					.setFirstUnchosenIndex(firstUnchosenIndex.get())
					.setNoUnacceptedValue(noMoreUnaccepted.get())
					.setResponseStatus(false)
					.setHostId(localHost.getHostID())
					.build();
		}
		for (int i = 0; i < acceptRequest.getFirstUnchosenIndex(); i++) {
			if (chosenArray.get(i) != null) {continue;}
			chosenArray.set(i, true);
			acceptedNotChosen.getAndDecrement();
		}
		acceptedValueArray.set(acceptRequest.getIndex(), acceptRequest.getValue());
		acceptedNotChosen.getAndIncrement();
		moveToNextUnchosenIndex();

		return AcceptorResponse.newBuilder().setHighestProposal(localProposalNumber.get())
				.setFirstUnchosenIndex(firstUnchosenIndex.get())
				.setNoUnacceptedValue(acceptedNotChosen.get() == 0)
				.setResponseStatus(true)
				.setHostId(localHost.getHostID())
				.build();
	}

	/**
	 * Send Success request to all hosts
	 */
	public Set<Future<AcceptorResponse>> sendSuccessRequestMsg(SuccessRequest successRequest) {
		Set<Future<AcceptorResponse>> responseSet = new HashSet<>();
		for (AbstractHost peer : hosts.hosts()) {
			if (localHost.equals(peer)) {continue;}
			Future<AcceptorResponse> futureResponse = executorService.submit(() -> {
				try {
					AcceptorResponse response = stubFactory.getBlockingStub(peer)
							.withDeadlineAfter(5, SECONDS)
							.makeSuccessMsg(successRequest);
					return response;
				} catch (Exception e) {
					logger.error("request failed " + e.getMessage());
					logger.error("peer addr " + peer.getAddress() + "peer port " + peer.getPort() + "if peer exits "
							+ String.valueOf(stubFactory.getBlockingStub(peer) != null));
					return AcceptorResponse.getDefaultInstance();
				}
			});
			responseSet.add(futureResponse);
		}
		return responseSet;
	}

	/**
	 * Process the SuccessRequest from leader and update local firstUnchosenIndex
	 */

	public AcceptorResponse processSuccessRequest(SuccessRequest successRequest) {
		logger.debug("receiving success request");
		if (!successRequest.getValue().equals(acceptedValueArray.get(successRequest.getIndex()))) {
			throw new RuntimeException(String.format("out of sync: leader has %s at %s and host %s has %s",
					successRequest.getValue(), successRequest.getIndex(), localHost.getHostID(),
					acceptedValueArray.get(successRequest.getIndex())));
		}
		if (chosenArray.get(successRequest.getIndex()) == null) {
			logger.debug("update chosen array");
			setChosen(successRequest.getIndex(), successRequest.getValue());
		}
		return AcceptorResponse.newBuilder().setHighestProposal(localProposalNumber.get())
				.setFirstUnchosenIndex(firstUnchosenIndex.get())
				.setNoUnacceptedValue(acceptedNotChosen.get() == 0)
				.setResponseStatus(true).build();
	}

	/**
	 * Process acceptorResponse recursively when acceptor has smaller firstUnchosenIndex
	 */

	public void processSuccessMsgResponse(Set<Future<AcceptorResponse>> responseSet)
			throws ExecutionException, InterruptedException {
		logger.debug("sending success request");
		logger.debug("local firstUnchosenIndex: " + firstUnchosenIndex.get());
		for (Future<AcceptorResponse> acceptorResponseFuture : responseSet) {
			AcceptorResponse acceptorResponse = acceptorResponseFuture.get();
			logger.debug("host " + acceptorResponse.getHostId() +
					" firstUnchosenIndex: " + acceptorResponse.getFirstUnchosenIndex());
			if (acceptorResponse.getFirstUnchosenIndex() < firstUnchosenIndex.get()) {
					SuccessRequest successRequest = SuccessRequest.newBuilder()
							.setIndex(acceptorResponse.getFirstUnchosenIndex())
							.setValue(acceptedValueArray.get(acceptorResponse.getFirstUnchosenIndex()))
							.build();
					logger.debug("sending success request");
				Set<Future<AcceptorResponse>> newResponseSet = sendSuccessRequestMsg(successRequest);
				processSuccessMsgResponse(newResponseSet);
			}
		}
	}

	public String toString() {
		int out = 0;
		for (int i = 0; i < firstUnchosenIndex.get(); i++) {
			if (chosenArray.get(i) != null) {
				out += Integer.valueOf(Math.abs(this.acceptedValueArray.get(i).hashCode()));
			}
		}
		return String.valueOf(out);
	}

	private void moveToNextUnchosenIndex() {
		while (chosenArray.get(firstUnchosenIndex.get()) != null) {
			firstUnchosenIndex.getAndIncrement();
		}
	}

	public boolean isMostUnaccepted() {
		return unacceptedStatusPeers.values().stream().filter(v -> v == true).count() >= (hosts.hosts().size() - 1) / 2;
	}

	public int getLocalProposalNumber() {
		return localProposalNumber.get();
	}

	public void setChosen(int index, String value) {
		chosenArray.set(index, true);
		acceptedValueArray.set(index, value);
		acceptedNotChosen.getAndIncrement();
		moveToNextUnchosenIndex();
	}

	public DataInsertionResponse redirectRequest(DataInsertionRequest request) {
		logger.debug(localHost + " redirecting " + request.toString() + " to leader - " + leader.get());
		logger.debug(String.valueOf("leader: " + leader.get() + " whether leader is found" + stubFactory.getBlockingStub(leader.get()) != null));
		try {
			DataInsertionResponse response = stubFactory.getBlockingStub(leader.get())
					.withDeadlineAfter(5, SECONDS)
					.createNewData(request);
			return response;
		} catch (Exception e) {
			logger.info(String.valueOf("leader: " + leader.get() + " whether leader is found" + stubFactory.getBlockingStub(leader.get()) != null));
			throw new RuntimeException(e);
		}
	}
}
