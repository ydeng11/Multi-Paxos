package today.ihelio.paxos;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import today.ihelio.paxoscomponents.DataInsertionRequest;
import today.ihelio.paxoscomponents.DataInsertionResponse;
import today.ihelio.paxoscomponents.PaxosServerServiceGrpc;

import static java.util.concurrent.TimeUnit.SECONDS;

public class PaxosClient{
  private static final Logger logger = LoggerFactory.getLogger(PaxosClient.class);
  private final ManagedChannel channel;
  private final PaxosServerServiceGrpc.PaxosServerServiceBlockingStub blockingStub;

  public PaxosClient(String host, int port) {
    channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
    blockingStub = PaxosServerServiceGrpc.newBlockingStub(channel);
  }

  public void shutdown() throws InterruptedException {
    channel.shutdown().awaitTermination(5, SECONDS);
  }

  public void createData() {
    DataInsertionRequest dataInsertionRequest = generateRandomeDataInsertionRequest();
    logger.info("Data Insertion Request: id: {}, idempotantKey: {}, key: {}, value: {}",
        dataInsertionRequest.getId(),
        dataInsertionRequest.getIdempotentKey(),
        dataInsertionRequest.getKey(),
        dataInsertionRequest.getValue());
    DataInsertionResponse response = blockingStub.withDeadlineAfter(5, SECONDS).createNewData(dataInsertionRequest);
    logger.info("response status: " + response.getStatus());
  }

  private DataInsertionRequest generateRandomeDataInsertionRequest() {
    final long currentTimeMillis = System.currentTimeMillis();
    return DataInsertionRequest.newBuilder()
        .setId(String.valueOf((currentTimeMillis & 0x0000_0000_FFFF_FFFFL) << 16))
        .setIdempotentKey(UUID.randomUUID().toString())
        .setKey((((currentTimeMillis >> 32) & 0xFFFF) << 16) + RandomStringUtils.randomAlphabetic(4))
        .setValue(RandomStringUtils.randomAlphabetic(10))
        .build();
  }

  public static void main(String[] args) throws InterruptedException {
    PaxosClient paxosClient = new PaxosClient("0.0.0.0", 14141);
    while (true) {
      Thread.sleep(5000);
      paxosClient.createData();
    }
  }
}
