package today.ihelio.paxos.common;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;

@Singleton
public class LifeCycleManager {
	Set<LifeCycleListener> lifeCycleListeners;
	
	@Inject
	public LifeCycleManager (Set<LifeCycleListener> lifeCycleListeners) {
		this.lifeCycleListeners = lifeCycleListeners;
	}
	
	public void Start() {
		for (LifeCycleListener lifeCycleListener : lifeCycleListeners) {
			lifeCycleListener.handleInitialization();
			
			lifeCycleListener.handleRunning();
			
		}
	}
	
	public void Stop() {
		for (LifeCycleListener lifeCycleListener : lifeCycleListeners) {
			lifeCycleListener.handlePreStopped();
			
			lifeCycleListener.handleStopping();
			
			lifeCycleListener.handleStopped();
		}
	}
	
}
