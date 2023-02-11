package today.ihelio.paxos.config;

import com.google.inject.AbstractModule;
import java.util.ArrayList;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import today.ihelio.paxos.utility.AbstractHost;
import today.ihelio.paxos.utility.Hosts;

public class ConfigModule extends AbstractModule {
	private final List<AbstractHost> hosts = new ArrayList<>();;
	private final String PORTS_YAML = "host_ports.yaml";
	private final String ADDRESS = "0.0.0.0";
	public ConfigModule () {
		Yaml yaml = new Yaml();
		InputStream inputStream = this.getClass()
				.getClassLoader()
				.getResourceAsStream(PORTS_YAML);
		Map<String, List<Integer>> yamlObj = yaml.load(inputStream);
		for (Integer port : yamlObj.get("ports")) {
			hosts.add(new AbstractHost(ADDRESS ,port));
		}
	}
	
	@Override
	protected void configure () {
		bind(Hosts.class).toInstance(new Hosts(this.hosts));
	}
}
