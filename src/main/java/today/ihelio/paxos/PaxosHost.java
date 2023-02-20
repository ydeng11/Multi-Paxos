package today.ihelio.paxos;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import today.ihelio.paxos.task.LeaderElectionTask;
import today.ihelio.paxos.task.PaxosConcensusTask;
import today.ihelio.paxos.utility.AbstractHost;

import static java.util.concurrent.TimeUnit.SECONDS;

@Singleton
public class PaxosHost {
    private static final Logger logger = LoggerFactory.getLogger(PaxosHost.class);
    private final PaxosServer paxosServer;
    private final AbstractHost localHost;
    private final Server server;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final LeaderElectionTask leaderElectionTask;
    private final PaxosConcensusTask paxosConcensusTask;
    @Inject
    public PaxosHost (@Named("LocalHost") AbstractHost localHost,
        PaxosServer paxosServer,
        LeaderProvider leaderProvider,
        LeaderElectionTask leaderElectionTask,
        PaxosConcensusTask paxosConcensusTask) {
        this.localHost = localHost;
        this.paxosServer = paxosServer;
        this.server = ServerBuilder.forPort(localHost.getPort()).addService(new PaxosService(paxosServer,
            leaderProvider)).build();
        this.leaderElectionTask = leaderElectionTask;
        this.paxosConcensusTask = paxosConcensusTask;
    }
    
    /** Start serving requests. */
    public void start() throws IOException {
        server.start();
        logger.info("Server started, listening on " + localHost.getPort());
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
        pool.submit(leaderElectionTask);
        pool.submit(paxosConcensusTask);
    }
    
    /** Stop serving requests and shutdown resources. */
    public void stop() throws InterruptedException {
        //paxosServer.shutDownManagedChannel();
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
