package today.ihelio.paxos;


import com.google.common.collect.ImmutableList;
import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import today.ihelio.paxoscomponents.HeartbeatRequest;
import today.ihelio.paxoscomponents.HeartbeatResponse;
import today.ihelio.paxoscomponents.PaxosServerServiceGrpc;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.SECONDS;

public class PaxosHost {
    private static final Logger logger = LoggerFactory.getLogger(PaxosHost.class);
    private final PaxosServer paxosServer = new PaxosServerImpl((int) PaxosServerUtil.getProcessID());
    private final int port;
    private final Server server;
    private final ConcurrentHashMap<Integer, ManagedChannel> channelForPeers;
    private final ConcurrentHashMap<Integer, PaxosServerServiceGrpc.PaxosServerServiceBlockingStub> blockingStubForPeers;
//    private final PaxosServerServiceGrpc.PaxosServerServiceStub asyncStub;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final String HOST = "0.0.0.0";
    private final List<Integer> peerHosts = ImmutableList.of(14141, 14142, 14143);
    
    public PaxosHost (int port) {
        this.port = port;
        this.server = ServerBuilder.forPort(port).addService(new PaxosService(paxosServer)).build();
        this.channelForPeers = new ConcurrentHashMap<>();
        this.blockingStubForPeers = new ConcurrentHashMap<>();
        for (int portID: peerHosts) {
            if (portID == port) {
                continue;
            }
            this.channelForPeers.putIfAbsent(portID, ManagedChannelBuilder.forAddress(HOST, portID).usePlaintext().build());
            this.blockingStubForPeers.putIfAbsent(portID, PaxosServerServiceGrpc.newBlockingStub(this.channelForPeers.get(portID)));
        }
    }
    public PaxosHost (int port, Server server, ConcurrentHashMap<Integer, ManagedChannel> channelForPeers,
                      ConcurrentHashMap<Integer, PaxosServerServiceGrpc.PaxosServerServiceBlockingStub> blockingStubForPeers) {
        this.port = port;
        this.server = server;
        this.channelForPeers = channelForPeers;
        this.blockingStubForPeers = blockingStubForPeers;
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
                    for (int peerPort: peerHosts) {
                        if (peerPort == port) {
                            continue;
                        }
                        HeartbeatRequest request = HeartbeatRequest.newBuilder().setHostId(paxosServer.getHostID()).build();
                        HeartbeatResponse response = HeartbeatResponse.getDefaultInstance();
                        try {
                            response = blockingStubForPeers.get(peerPort).withDeadlineAfter(5, SECONDS).sendHeartBeat(request);
                        } catch (Exception e) {
                            logger.error("request failed " + e.getMessage());
                        }
                        logger.info(response.toString());
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
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
    
    /**
     * Main method.  This comment makes the linter happy.
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
			logger.error("no arguments passed!");
            System.exit(1);
        }
        int port = Integer.valueOf(args[0]);
        PaxosHost host = new PaxosHost(port);
        host.start();
        host.blockUntilShutdown();
    }

}