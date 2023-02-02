package today.ihelio.paxos;

public interface PaxosServer {
	boolean isLeader();
	void updateLeadership(boolean isLeader);
	int getHostID ();
//	AcceptorResponse processProposal(Proposal proposal);
//	AcceptorResponse processAccept(AcceptRequest acceptRequest);
}
