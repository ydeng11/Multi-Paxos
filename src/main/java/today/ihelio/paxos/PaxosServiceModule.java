package today.ihelio.paxos;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import java.util.HashMap;
import today.ihelio.paxos.config.ConfigModule;
import today.ihelio.paxos.utility.AbstractHost;
import today.ihelio.paxos.annotations.Leader;
import today.ihelio.paxos.utility.StubFactory;

public class PaxosServiceModule extends AbstractModule {
	AbstractHost host;
	public PaxosServiceModule(AbstractHost host) {
		this.host = host;
	}

	@Override
	protected void configure () {
		install(new ConfigModule());
		bind(LeaderProvider.class).toInstance(new LeaderProvider(host));
		bind(AbstractHost.class).annotatedWith(Leader.class).toProvider(LeaderProvider.class);
		bind(AbstractHost.class).annotatedWith(Names.named("LocalHost")).toInstance(host);
		bind(StubFactory.class).toInstance(new StubFactory(new HashMap<>(), new HashMap<>()));
	}
}
