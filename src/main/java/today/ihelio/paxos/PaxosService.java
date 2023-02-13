package today.ihelio.paxos;

import io.grpc.stub.StreamObserver;
import javax.inject.Inject;
import today.ihelio.paxos.utility.Leader;
import today.ihelio.paxoscomponents.AcceptRequest;
import today.ihelio.paxoscomponents.AcceptorResponse;
import today.ihelio.paxoscomponents.PrepareRequest;
import today.ihelio.paxoscomponents.PrepareResponse;
import today.ihelio.paxoscomponents.Proposal;

public class PaxosService extends today.ihelio.paxoscomponents.PaxosServerServiceGrpc.PaxosServerServiceImplBase {
	private final PaxosServer paxosServer;
	private final LeaderProvider leaderProvider;

	@Inject
	public PaxosService (PaxosServer paxosServer, LeaderProvider leaderProvider) {
		this.paxosServer = paxosServer;
		this.leaderProvider = leaderProvider;
	}
	
	@Override
	public void makeProposalMsg (PrepareRequest request, StreamObserver<PrepareResponse> responseObserver) {
		today.ihelio.paxoscomponents.Proposal proposal = request.getProposal();
		Proposal responseProposal = paxosServer.processProposal(proposal);
		PrepareResponse prepareResponse = PrepareResponse.newBuilder().setProposal(responseProposal).build();
		responseObserver.onNext(prepareResponse);
		responseObserver.onCompleted();
	}
	
	@Override
	public void makeAcceptMsg (AcceptRequest request, StreamObserver<AcceptorResponse> responseObserver) {
		super.makeAcceptMsg(request, responseObserver);
	}
	
	@Override
	public void makeSuccessMsg (today.ihelio.paxoscomponents.SuccessRequest request, StreamObserver<AcceptorResponse> responseObserver) {
		super.makeSuccessMsg(request, responseObserver);
	}
	
	@Override
	public void sendHeartBeat (today.ihelio.paxoscomponents.HeartbeatRequest request, StreamObserver<today.ihelio.paxoscomponents.HeartbeatResponse> responseObserver) {
		int requestHostId = Integer.valueOf(request.getHostId());
		String requestAddress = request.getAddress();
		int requestHostPort = Integer.valueOf(request.getPort());
		leaderProvider.processHeartbeat(new Leader(requestHostId, requestAddress, requestHostPort));
		responseObserver.onNext(today.ihelio.paxoscomponents.HeartbeatResponse.newBuilder().setReceived(true).build());
		responseObserver.onCompleted();
	}
}
