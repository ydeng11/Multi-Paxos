package today.ihelio.paxos;

import io.grpc.stub.StreamObserver;
import today.ihelio.paxoscomponents.*;

public class PaxosService extends PaxosServerServiceGrpc.PaxosServerServiceImplBase {
	@Override
	public void makeProposalMsg (PrepareRequest request, StreamObserver<AcceptorResponse> responseObserver) {
	}
	
	@Override
	public void makeAcceptMsg (AcceptRequest request, StreamObserver<AcceptorResponse> responseObserver) {
		super.makeAcceptMsg(request, responseObserver);
	}
	
	@Override
	public void makeSuccessMsg (SuccessRequest request, StreamObserver<AcceptorResponse> responseObserver) {
		super.makeSuccessMsg(request, responseObserver);
	}
	
	@Override
	public void sendHeartBeat (HeartbeatRequest request, StreamObserver<HeartbeatResponse> responseObserver) {
		super.sendHeartBeat(request, responseObserver);
	}
}
