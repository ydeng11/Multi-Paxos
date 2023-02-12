package today.ihelio.paxos.utility;

public class Leader extends AbstractHost{
  public Leader(int hostID, String address, int port) {
    super(hostID, address, port);
  }

  public static Leader of(AbstractHost abstractHost) {
    return new Leader(abstractHost.getHostID(), abstractHost.getAddress(), abstractHost.getPort());
  }

}
