package marcianos.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import marcianos.InputCommand;
import marcianos.OnlineSessionConfig;

/** TCP client for loopback/LAN/Internet server integration using text-line protocol. */
public final class NetworkClient {
    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private final OnlineSessionConfig config;
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private volatile String lastError = "";
    private volatile RemoteSnapshot latestSnapshot;
    private volatile int localPlayerId = -1;
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private Thread connectThread;
    private Thread readThread;

    public NetworkClient(OnlineSessionConfig config) {
        this.config = config;
    }

    public void connect() {
        if (state == ConnectionState.CONNECTING || state == ConnectionState.CONNECTED) return;
        state = ConnectionState.CONNECTING;
        lastError = "";
        connectThread = new Thread(new Runnable() {
            @Override public void run() {
                doConnect();
            }
        }, "marcianos-net-connect");
        connectThread.setDaemon(true);
        connectThread.start();
    }

    public void disconnect() {
        closeSocket();
        state = ConnectionState.DISCONNECTED;
    }

    public void sendInput(InputCommand input) {
        if (state != ConnectionState.CONNECTED || writer == null) return;
        synchronized (writer) {
            writer.println(TcpProtocol.inputMessage(input));
        }
    }

    public void requestRespawn() {
        if (state != ConnectionState.CONNECTED || writer == null) return;
        synchronized (writer) {
            writer.println(TcpProtocol.respawnMessage());
        }
    }

    public void update(float delta) {
        // Network work is done by background threads. The render loop polls latest state.
    }

    public ConnectionState state() { return state; }
    public String lastError() { return lastError; }
    public OnlineSessionConfig config() { return config; }
    public RemoteSnapshot latestSnapshot() { return latestSnapshot; }

    private void doConnect() {
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress(config.connectHost(), config.port()), 2000);
            socket = s;
            writer = new PrintWriter(socket.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

            synchronized (writer) {
                writer.println(TcpProtocol.joinMessage(config.playerName()));
            }

            readThread = new Thread(new Runnable() {
                @Override public void run() {
                    readLoop();
                }
            }, "marcianos-net-read");
            readThread.setDaemon(true);
            readThread.start();
        } catch (IOException ex) {
            state = ConnectionState.ERROR;
            lastError = "Connection failed: " + ex.getMessage();
            closeSocket();
        }
    }

    private void readLoop() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                int welcomeId = TcpProtocol.parseWelcomePlayerId(line);
                if (welcomeId > 0) {
                    localPlayerId = welcomeId;
                    state = ConnectionState.CONNECTED;
                    continue;
                }
                String protocolError = TcpProtocol.parseError(line);
                if (protocolError != null) {
                    state = ConnectionState.ERROR;
                    lastError = protocolError;
                    continue;
                }
                RemoteSnapshot parsed = TcpProtocol.parseSnapshot(line, localPlayerId);
                if (parsed != null) latestSnapshot = parsed;
            }
            if (state != ConnectionState.ERROR) {
                state = ConnectionState.DISCONNECTED;
                lastError = "Disconnected by server.";
            }
        } catch (IOException ex) {
            if (state != ConnectionState.DISCONNECTED) {
                state = ConnectionState.ERROR;
                lastError = "Network read error: " + ex.getMessage();
            }
        } finally {
            closeSocket();
        }
    }

    private void closeSocket() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        socket = null;
        writer = null;
        reader = null;
    }
}
