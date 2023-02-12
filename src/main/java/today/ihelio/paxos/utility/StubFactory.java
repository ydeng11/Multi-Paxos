package today.ihelio.paxos.utility;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import today.ihelio.paxoscomponents.PaxosServerServiceGrpc;

@Singleton
public class StubFactory {
  private final Map<AbstractHost, PaxosServerServiceGrpc.PaxosServerServiceBlockingStub> blockingStubMap;

  public StubFactory(
      Map<AbstractHost, PaxosServerServiceGrpc.PaxosServerServiceBlockingStub> blockingStubMap) {
    this.blockingStubMap = blockingStubMap;
  }

  private ManagedChannel createChannel(AbstractHost host) {
    return ManagedChannelBuilder.forAddress(host.getAddress(), host.getPort()).usePlaintext().build();
  }

  public PaxosServerServiceGrpc.PaxosServerServiceBlockingStub createBlockingStub(AbstractHost host) {
    blockingStubMap.putIfAbsent(host, PaxosServerServiceGrpc.newBlockingStub(createChannel(host)));
    return blockingStubMap.get(host);
  }

  public PaxosServerServiceGrpc.PaxosServerServiceBlockingStub getBlockingStub(AbstractHost host) {
    return createBlockingStub(host);
  }
}
