package today.ihelio.paxos.utility;

import com.google.inject.Singleton;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import today.ihelio.paxoscomponents.PaxosServerServiceGrpc;

@Singleton
public class StubFactory {
  private final Map<AbstractHost, PaxosServerServiceGrpc.PaxosServerServiceBlockingStub> blockingStubMap;
  private final Map<AbstractHost, ManagedChannel> managedChannelMap;

  public StubFactory(
      Map<AbstractHost, PaxosServerServiceGrpc.PaxosServerServiceBlockingStub> blockingStubMap,
      Map<AbstractHost, ManagedChannel> managedChannelMap) {
    this.blockingStubMap = blockingStubMap;
    this.managedChannelMap = managedChannelMap;
  }

  private ManagedChannel createChannel(AbstractHost host) {
    if (!managedChannelMap.containsKey(host)) {
      ManagedChannel managedChannel =
          ManagedChannelBuilder.forAddress(host.getAddress(), host.getPort())
              .usePlaintext()
              .idleTimeout(5, TimeUnit.SECONDS)
              .build();
      managedChannelMap.putIfAbsent(host, managedChannel);
    }
    return managedChannelMap.get(host);
  }

  public PaxosServerServiceGrpc.PaxosServerServiceBlockingStub createBlockingStub(AbstractHost host) {
    blockingStubMap.putIfAbsent(host, PaxosServerServiceGrpc.newBlockingStub(createChannel(host)));
    return blockingStubMap.get(host);
  }

  public PaxosServerServiceGrpc.PaxosServerServiceBlockingStub getBlockingStub(AbstractHost host) {
    return createBlockingStub(host);
  }
}
