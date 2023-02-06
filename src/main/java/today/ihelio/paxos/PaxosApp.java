package today.ihelio.paxos;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import today.ihelio.paxos.utility.HostPorts;

import static com.google.common.base.Preconditions.checkNotNull;

public class PaxosApp {
	private static final Logger logger = LoggerFactory.getLogger(PaxosApp.class);
	public static void main(String[] args) throws Exception {
		Injector injector = Guice.createInjector(new PaxosServiceModule());
		HostPorts hostPorts = injector.getInstance(HostPorts.class);
		checkNotNull(hostPorts);
		int port = Integer.valueOf(args[0]);
		PaxosHost host = new PaxosHost(port, hostPorts);
		host.start();
		host.blockUntilShutdown();
	}
}
