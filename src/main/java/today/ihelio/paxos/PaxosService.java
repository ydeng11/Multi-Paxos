package today.ihelio.paxos;

import io.grpc.stub.StreamObserver;
import today.ihelio.paxoscomponents.AcceptRequest;
import today.ihelio.paxoscomponents.AcceptorResponse;
import today.ihelio.paxoscomponents.HeartbeatRequest;
import today.ihelio.paxoscomponents.HeartbeatResponse;

public class PaxosService extends today.ihelio.paxoscomponents.PaxosServerServiceGrpc.PaxosServerServiceImplBase {
	private final PaxosServer paxosServer;
	private final LeaderElectionTask leaderElectionTask;
	
	public PaxosService (PaxosServer paxosServer) {
		this.paxosServer = paxosServer;
		this.leaderElectionTask = new LeaderElectionTask(paxosServer, paxosServer.getHostID());
	}
	
	@Override
	public void makeProposalMsg (today.ihelio.paxoscomponents.PrepareRequest request, StreamObserver<AcceptorResponse> responseObserver) {
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
	public void sendHeartBeat (HeartbeatRequest request, StreamObserver<HeartbeatResponse> responseObserver) {
		int requestHostId = Integer.valueOf(request.getHostId());
		leaderElectionTask.processHeartbeat(requestHostId);
		responseObserver.onNext(HeartbeatResponse.newBuilder().setReceived(true).build());
		responseObserver.onCompleted();
	}
}
