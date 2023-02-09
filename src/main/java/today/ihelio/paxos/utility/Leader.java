package today.ihelio.paxos.utility;

public class Leader extends AbstractHost{
  public Leader(int hostID, int port) {
    super(hostID, port);
  }

  public static Leader of(AbstractHost abstractHost) {
    return new Leader(abstractHost.getHostID(), abstractHost.getPort());
  }

}
