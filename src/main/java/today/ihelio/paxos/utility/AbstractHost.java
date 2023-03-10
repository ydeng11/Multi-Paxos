package today.ihelio.paxos.utility;

public class AbstractHost {
  int hostID;
  String address;
  int port;

  public AbstractHost(int hostID, String address, int port) {
    this.hostID = hostID;
    this.address = address;
    this.port = port;
  }

  public AbstractHost(String address, int port) {
    this.hostID = -1;
    this.address = address;
    this.port = port;
  }

  public int getHostID() {
    return hostID;
  }

  public void setHostID(int hostID) {
    this.hostID = hostID;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }


  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  @Override public boolean equals(Object obj) {
    if (!(obj instanceof AbstractHost)) {
      return false;
    }
    return this.address.equals(((AbstractHost)obj).getAddress()) &&
        this.port == (((AbstractHost)obj).getPort());
  }

  @Override public String toString() {
    return "hostID: " + hostID + " address: " + address + " port: " + port;
  }
}
