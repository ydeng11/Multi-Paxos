package today.ihelio.paxos;


import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import today.ihelio.paxos.utility.HostPorts;
import today.ihelio.paxos.utility.PaxosServerUtil;
import today.ihelio.paxoscomponents.HeartbeatRequest;
import today.ihelio.paxoscomponents.HeartbeatResponse;
import today.ihelio.paxoscomponents.PaxosServerServiceGrpc;

import javax.inject.Inject;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.SECONDS;

public class PaxosHost {
    private static final Logger logger = LoggerFactory.getLogger(PaxosHost.class);
    private final PaxosServer paxosServer;
    private final int port;
    private final Server server;
    private final ConcurrentHashMap<Integer, ManagedChannel> channelForPeers;
    private final ConcurrentHashMap<Integer, PaxosServerServiceGrpc.PaxosServerServiceBlockingStub> blockingStubForPeers;
//    private final PaxosServerServiceGrpc.PaxosServerServiceStub asyncStub;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final String HOST = "0.0.0.0";
    @Inject
    private final HostPorts hostPorts;

    public PaxosHost (int port, HostPorts hostPorts) {
        this.port = port;
        this.paxosServer = new PaxosServer((int) PaxosServerUtil.getProcessID(), hostPorts);
        this.server = ServerBuilder.forPort(port).addService(new PaxosService(paxosServer)).build();
        this.channelForPeers = new ConcurrentHashMap<>();
        this.blockingStubForPeers = new ConcurrentHashMap<>();
        for (int portID: hostPorts.ports()) {
            if (portID == port) {
                continue;
            }
            this.channelForPeers.putIfAbsent(portID, ManagedChannelBuilder.forAddress(HOST, portID).usePlaintext().build());
            this.blockingStubForPeers.putIfAbsent(portID, PaxosServerServiceGrpc.newBlockingStub(this.channelForPeers.get(portID)));
        }
        this.hostPorts = hostPorts;
    }
    public PaxosHost (PaxosServer paxosServer, int port, Server server, ConcurrentHashMap<Integer, ManagedChannel> channelForPeers,
                      ConcurrentHashMap<Integer, PaxosServerServiceGrpc.PaxosServerServiceBlockingStub> blockingStubForPeers, HostPorts hostPorts) {
        this.paxosServer = paxosServer;
        this.port = port;
        this.server = server;
        this.channelForPeers = channelForPeers;
        this.blockingStubForPeers = blockingStubForPeers;
        this.hostPorts = hostPorts;
    }
    
    /** Start serving requests. */
    public void start() throws IOException {
        server.start();
        logger.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                try {
                    PaxosHost.this.stop();
                } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                }
                System.err.println("*** server shut down");
            }
        });
        pool.submit(new Runnable() {
            @Override
            public void run () {
                while (true) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    for (int peerPort: hostPorts.ports()) {
                        if (peerPort == port) {
                            continue;
                        }
                        HeartbeatRequest request = HeartbeatRequest.newBuilder().setHostId(paxosServer.getHostID()).build();
                        HeartbeatResponse response = HeartbeatResponse.getDefaultInstance();
                        try {
                            response = blockingStubForPeers.get(peerPort).withDeadlineAfter(5, SECONDS).sendHeartBeat(request);
                            logger.info(response.toString());
                        } catch (Exception e) {
                            logger.error("request failed " + e.getMessage());
                        }
                    }
                    }
                }
            });
    }
    
    /** Stop serving requests and shutdown resources. */
    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, SECONDS);
        }
        pool.shutdown();
    }
    
    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    void blockUntilShutdown () throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
}
