public class ServerId {

    private final String serverName;
    private final String daemon;
    private final int port;

    public ServerId(String serverName, String daemon, int port) {
        this.serverName = serverName;
        this.daemon = daemon;
        this.port = port;
    }

    public String getId(){
        return "#" + serverName + "#" + daemon;
    }

    public int getPort() {
        return port;
    }
}
