package today.ihelio.paxos.config;

import com.google.inject.AbstractModule;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import today.ihelio.paxos.utility.HostPorts;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class ConfigModule extends AbstractModule {
	private final HostPorts hostPorts;
	private final String PORTS_YAML = "host_ports.yaml";
	public ConfigModule () {
		Yaml yaml = new Yaml(new Constructor(HostPorts.class));
		InputStream inputStream = this.getClass()
				.getClassLoader()
				.getResourceAsStream(PORTS_YAML);
		HostPorts hostPorts = yaml.load(inputStream);
		Map<String, List<Integer>> yamlObj = yaml.load(inputStream);
		this.hostPorts = new HostPorts(yamlObj.get("ports"));
	}
	
	@Override
	protected void configure () {
		bind(HostPorts.class).toInstance(this.hostPorts);
	}
}