package marcianos;

/** Immutable connection settings used to start an online session. */
public final class OnlineSessionConfig {
    private final boolean hostMode;
    private final String connectHost;
    private final String bindHost;
    private final int port;
    private final String playerName;

    public OnlineSessionConfig(boolean hostMode, String host, int port, String playerName) {
        this(hostMode, host, port, playerName, hostMode ? "0.0.0.0" : "");
    }

    public OnlineSessionConfig(boolean hostMode, String connectHost, int port, String playerName,
                               String bindHost) {
        this.hostMode = hostMode;
        this.connectHost = connectHost;
        this.bindHost = bindHost;
        this.port = port;
        this.playerName = playerName;
    }

    public boolean hostMode() { return hostMode; }
    public String connectHost() { return connectHost; }
    public String bindHost() { return bindHost; }
    public String host() { return connectHost; }
    public int port() { return port; }
    public String playerName() { return playerName; }
}
