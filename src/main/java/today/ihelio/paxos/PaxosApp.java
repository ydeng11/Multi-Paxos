package today.ihelio.paxos;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import today.ihelio.paxos.utility.AbstractHost;
import today.ihelio.paxos.utility.Hosts;

import static com.google.common.base.Preconditions.checkNotNull;

public class PaxosApp {
	private static final Logger logger = LoggerFactory.getLogger(PaxosApp.class);
	public static void main(String[] args) throws Exception {
		long hostId = ProcessHandle.current().pid();
		int port = Integer.valueOf(args[0]);
		String address = "0.0.0.0";
		AbstractHost localHost = new AbstractHost((int) hostId, address, port);
		Injector injector = Guice.createInjector(new PaxosServiceModule(localHost));
		Hosts hosts = injector.getInstance(Hosts.class);
		LeaderProvider leaderProvider = injector.getInstance(LeaderProvider.class);
		PaxosServer paxosServer = injector.getInstance(PaxosServer.class);
		checkNotNull(hosts);
		PaxosHost host = new PaxosHost(localHost, paxosServer, leaderProvider);
		host.start();
		host.blockUntilShutdown();
	}
}
