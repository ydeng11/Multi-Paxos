package today.ihelio.paxos;

import com.google.inject.AbstractModule;
import today.ihelio.paxos.config.ConfigModule;

public class PaxosServiceModule extends AbstractModule {
	@Override
	protected void configure () {
		install(new ConfigModule());
	}
}
