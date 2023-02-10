package today.ihelio.paxos;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import today.ihelio.paxos.config.ConfigModule;
import today.ihelio.paxos.utility.AbstractHost;
import today.ihelio.paxos.utility.Leader;

public class PaxosServiceModule extends AbstractModule {
	AbstractHost host;
	public PaxosServiceModule(AbstractHost host) {
		this.host = host;
	}

	@Override
	protected void configure () {
		install(new ConfigModule());
		bind(LeaderProvider.class).toInstance(new LeaderProvider(host));
		bind(Leader.class).toProvider(LeaderProvider.class);
		bind(AbstractHost.class).annotatedWith(Names.named("LocalHost")).toInstance(host);
	}
}
