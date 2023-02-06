package today.ihelio.paxos.common;

public interface LifeCycleListener {
	void handleInitialization();
	void handleRunning();
	void handlePreStopped();
	void handleStopping();
	void handleStopped();
}
