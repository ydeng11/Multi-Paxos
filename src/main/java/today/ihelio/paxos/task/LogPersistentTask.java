package today.ihelio.paxos.task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import today.ihelio.paxos.PaxosServer;

@Singleton
public class LogPersistentTask implements Runnable {
  private final Logger logger = LoggerFactory.getLogger(LogPersistentTask.class);
  private final PaxosServer paxosServer;
  private final Path FILEPATH;
  @Inject
  public LogPersistentTask(PaxosServer paxosServer) throws IOException {
    this.paxosServer = paxosServer;
    this.FILEPATH = Paths.get("/Users/ydeng/code/Multi-Paxos/FakePersistentStore/"
        + paxosServer.getHostID() + ".txt");
    if (!Files.exists(FILEPATH)) {
      Files.createFile(FILEPATH);
    }
  }

  @Override public void run() {
    while (true) {
      try {
        Thread.sleep(5000);
        Files.writeString(FILEPATH, String.valueOf(System.currentTimeMillis())
            + " - Hash: " + paxosServer + "\n", StandardOpenOption.APPEND);
        logger.debug("valueArray hash: " + paxosServer + "\n");
        logger.debug("firstUnchosen: " + paxosServer.getFirstUnchosenIndex());
        logger.debug("hash: " + paxosServer);
      }
      catch (InterruptedException e) {
        logger.info("InterruptedException: " + e.getMessage());
        throw new RuntimeException(e);
      }
      catch (IOException e) {
        logger.info("Got IOException: " + e.getMessage());
        throw new RuntimeException(e);
      }
    }
  }
}
