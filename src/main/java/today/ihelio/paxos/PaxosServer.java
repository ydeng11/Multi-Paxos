package today.ihelio.paxos;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

public class PaxosServer {
	private final int hostId;
	private final AtomicReference<Boolean> noMoreAccepted = new AtomicReference<>();
	private final AtomicReference<Integer> firstUnchosenIndex = new AtomicReference<>();
	private final AtomicReference<Integer> leaderServer = new AtomicReference<>();
	private final List<String> serverLists;
	private final LinkedList<Boolean> proposalList;
	private final LinkedList<Integer> keys;
	private final LinkedList<Integer> values;
	private final LinkedList<Integer> chosen;
	private final ExecutorService executorService;
	
	public PaxosServer (int hostId,
	                    LinkedList<Boolean> proposalList,
	                    LinkedList<Integer> keys, LinkedList<Integer> values, LinkedList<Integer> chosen,
	                    ExecutorService executorService,
	                    ArrayList<String> serverLists) {
		this.hostId = hostId;
		this.proposalList = proposalList;
		this.keys = keys;
		this.values = values;
		this.chosen = chosen;
		this.noMoreAccepted.set(false);
		this.firstUnchosenIndex.set(0);
		this.executorService = executorService;
		this.serverLists = serverLists;
	}
	
	public void sendHeartBeats() {
		for (String server : serverLists) {
		
		}
	}
	
}
