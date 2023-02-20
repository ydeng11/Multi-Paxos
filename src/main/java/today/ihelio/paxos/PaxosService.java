package today.ihelio.paxos;

import io.grpc.stub.StreamObserver;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import today.ihelio.paxos.utility.Leader;
import today.ihelio.paxoscomponents.AcceptRequest;
import today.ihelio.paxoscomponents.AcceptorResponse;
import today.ihelio.paxoscomponents.DataInsertionRequest;
import today.ihelio.paxoscomponents.DataInsertionResponse;
import today.ihelio.paxoscomponents.HeartbeatRequest;
import today.ihelio.paxoscomponents.HeartbeatResponse;
import today.ihelio.paxoscomponents.PrepareRequest;
import today.ihelio.paxoscomponents.PrepareResponse;
import today.ihelio.paxoscomponents.Proposal;
import today.ihelio.paxoscomponents.SuccessRequest;

public class PaxosService extends today.ihelio.paxoscomponents.PaxosServerServiceGrpc.PaxosServerServiceImplBase {
	private final static Logger logger = LoggerFactory.getLogger(PaxosService.class);
	private final PaxosServer paxosServer;
	private final LeaderProvider leaderProvider;

	@Inject
	public PaxosService (PaxosServer paxosServer, LeaderProvider leaderProvider) {
		this.paxosServer = paxosServer;
		this.leaderProvider = leaderProvider;
	}

	@Override public void createNewData(DataInsertionRequest request,
			StreamObserver<DataInsertionResponse> responseObserver) {
		DataInsertionResponse.Builder responseBuilder = DataInsertionResponse.newBuilder()
				.setId(request.getId())
				.setKey(request.getKey())
				.setValue(request.getValue());
		logger.info("receive request");
		if (paxosServer.isLeader()) {
			paxosServer.addClientRequest(request);
			responseBuilder.setStatus("processing");
			responseObserver.onNext(responseBuilder.build());
		} else {
			responseObserver.onNext(paxosServer.redirectRequest(request));
		}
		responseObserver.onCompleted();
	}

	@Override
	public void makeProposalMsg (PrepareRequest request, StreamObserver<PrepareResponse> responseObserver) {
		Proposal proposal = request.getProposal();
		Proposal responseProposal = paxosServer.processProposalRequest(proposal);
		PrepareResponse prepareResponse = PrepareResponse.newBuilder().setProposal(responseProposal).build();
		responseObserver.onNext(prepareResponse);
		responseObserver.onCompleted();
	}
	
	@Override
	public void makeAcceptMsg (AcceptRequest request, StreamObserver<AcceptorResponse> responseObserver) {
		AcceptorResponse acceptorResponse = paxosServer.processAcceptRequest(request);
		responseObserver.onNext(acceptorResponse);
		responseObserver.onCompleted();
	}
	
	@Override
	public void makeSuccessMsg (SuccessRequest request, StreamObserver<AcceptorResponse> responseObserver) {
		AcceptorResponse successResponse = paxosServer.processSuccessRequest(request);
		responseObserver.onNext(successResponse);
		responseObserver.onCompleted();
	}
	
	@Override
	public void sendHeartBeat (HeartbeatRequest request, StreamObserver<HeartbeatResponse> responseObserver) {
		int requestHostId = Integer.valueOf(request.getHostId());
		String requestAddress = request.getAddress();
		int requestHostPort = Integer.valueOf(request.getPort());
		leaderProvider.processHeartbeat(new Leader(requestHostId, requestAddress, requestHostPort));
		responseObserver.onNext(today.ihelio.paxoscomponents.HeartbeatResponse.newBuilder().setReceived(true).build());
		responseObserver.onCompleted();
	}
}
