package today.ihelio.paxos.utility;

public class AbstractHost {
  int hostID;
  int port;

  public AbstractHost(int hostID, int port) {
    this.hostID = hostID;
    this.port = port;
  }

  public int getHostID() {
    return hostID;
  }

  public void setHostID(int hostID) {
    this.hostID = hostID;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }
}
