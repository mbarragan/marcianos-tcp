package marcianos;

/** Immutable connection settings used to start an online session. */
public final class OnlineSessionConfig {
    private final boolean hostMode;
    private final String host;
    private final int port;
    private final String playerName;

    public OnlineSessionConfig(boolean hostMode, String host, int port, String playerName) {
        this.hostMode = hostMode;
        this.host = host;
        this.port = port;
        this.playerName = playerName;
    }

    public boolean hostMode() { return hostMode; }
    public String host() { return host; }
    public int port() { return port; }
    public String playerName() { return playerName; }
}
