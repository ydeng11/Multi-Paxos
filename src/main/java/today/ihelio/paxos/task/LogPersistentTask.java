package today.ihelio.paxos.task;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import javax.inject.Inject;
import javax.inject.Singleton;
import today.ihelio.paxos.PaxosServer;

@Singleton
public class LogPersistentTask implements Runnable {
  private final PaxosServer paxosServer;
  private long lastSeen;

  @Inject
  public LogPersistentTask(PaxosServer paxosServer) {
    this.paxosServer = paxosServer;
    lastSeen = System.currentTimeMillis();
  }

  @Override public void run() {
    while (true) {
      if (System.currentTimeMillis() - lastSeen > 5000) {
        try (FileWriter fw = new FileWriter("/Users/ydeng/code/Multi-Paxos/FakePersistentStore/"
            + paxosServer.getHostID() + ".txt", true)) {
          BufferedWriter bw = new BufferedWriter(fw);
          PrintWriter out = new PrintWriter(bw);
          out.println(paxosServer);
          out.println();
        } catch (FileNotFoundException e) {
          throw new RuntimeException(e);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        lastSeen = System.currentTimeMillis();
      }
    }
  }
}
