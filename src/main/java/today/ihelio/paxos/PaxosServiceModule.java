package today.ihelio.paxos;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import org.apache.logging.log4j.core.util.Loader;
import today.ihelio.paxos.config.ConfigModule;
import today.ihelio.paxos.utility.AbstractHost;
import today.ihelio.paxos.utility.HostPorts;
import today.ihelio.paxos.utility.Leader;

public class PaxosServiceModule extends AbstractModule {
	AbstractHost host;
	public PaxosServiceModule(AbstractHost host) {
		this.host = host;
	}

	@Override
	protected void configure () {
		install(new ConfigModule());
		bind(LeaderElectionTask.class).toInstance(new LeaderElectionTask(host));
		bind(Leader.class).toProvider(LeaderElectionTask.class);
		bind(PaxosServer.class).to(PaxosServer.class);
		bind(AbstractHost.class).annotatedWith(Names.named("LocalHost")).toInstance(host);
	}
}
