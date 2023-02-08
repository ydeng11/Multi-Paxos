package today.ihelio.paxos;

import io.grpc.stub.StreamObserver;
import today.ihelio.paxoscomponents.AcceptRequest;
import today.ihelio.paxoscomponents.AcceptorResponse;
import today.ihelio.paxoscomponents.PrepareRequest;
import today.ihelio.paxoscomponents.PrepareResponse;

public class PaxosService extends today.ihelio.paxoscomponents.PaxosServerServiceGrpc.PaxosServerServiceImplBase {
	private final PaxosServer paxosServer;
	private final LeaderElectionTask leaderElectionTask;
	
	public PaxosService (PaxosServer paxosServer) {
		this.paxosServer = paxosServer;
		this.leaderElectionTask = new LeaderElectionTask(paxosServer, paxosServer.getHostID());
	}
	
	@Override
	public void makeProposalMsg (PrepareRequest request, StreamObserver<PrepareResponse> responseObserver) {
		today.ihelio.paxoscomponents.Proposal proposal = request.getProposal();
		//paxosServer.processProposal(proposal);
		PrepareResponse prepareResponse = PrepareResponse.newBuilder().setProposal(proposal).build();
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
		leaderElectionTask.processHeartbeat(requestHostId);
		responseObserver.onNext(today.ihelio.paxoscomponents.HeartbeatResponse.newBuilder().setReceived(true).build());
		responseObserver.onCompleted();
	}
}
