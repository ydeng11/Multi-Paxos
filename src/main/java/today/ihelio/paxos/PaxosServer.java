package today.ihelio.paxos;

public interface PaxosServer {
	boolean isLeader();
	void updateLeadership(int leaderId);
	int getHostID ();
	int getLeaderID ();
//	AcceptorResponse processProposal(Proposal proposal);
//	AcceptorResponse processAccept(AcceptRequest acceptRequest);
}
