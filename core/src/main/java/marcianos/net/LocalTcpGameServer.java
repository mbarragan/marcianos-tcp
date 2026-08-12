package marcianos.net;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import marcianos.Asteroid;
import marcianos.Bullet;
import marcianos.InputCommand;
import marcianos.PlayerManager;
import marcianos.ShipExplosionSink;
import marcianos.Star;

/** Lightweight TCP server used to validate online simulation with desktop rules. */
public final class LocalTcpGameServer {
    private static final int MAX_PLAYERS = 16;
    private static final float SHIP_COLLISION_DISTANCE = 32f;
    private static final float BULLET_HIT_DISTANCE = 18f;
    private static final float ASTEROID_APPEARANCE_TIME = 10f;
    private static final float ASTEROID_SPAWN_INTERVAL = 20f;
    private static final float STAR_APPEARANCE_TIME = 30f;
    private static final float STAR_MASS_INTERVAL = 9f;
    private static final Map<Integer, LocalTcpGameServer> RUNNING = new ConcurrentHashMap<>();

    public static synchronized void startIfNeeded(int port) throws IOException {
        startIfNeeded("0.0.0.0", port);
    }

    public static synchronized void startIfNeeded(String bindHost, int port) throws IOException {
        if (RUNNING.containsKey(port)) return;
        LocalTcpGameServer server = new LocalTcpGameServer(bindHost, port);
        server.start();
        RUNNING.put(port, server);
    }

    public static synchronized void stopIfRunning(int port) {
        LocalTcpGameServer server = RUNNING.remove(port);
        if (server != null) server.stop();
    }

    private final String bindHost;
    private final int port;
    private final List<ClientConnection> clients =
        Collections.synchronizedList(new ArrayList<ClientConnection>());
    private final Map<Integer, ServerPlayerState> players = new HashMap<>();
    private final List<Asteroid> asteroids = new ArrayList<>();
    private final List<TcpProtocol.SnapshotExplosion> pendingExplosions = new ArrayList<>();
    private final Set<Long> activeShipCollisions = new HashSet<>();
    private final ShipExplosionSink explosionSink = new ShipExplosionSink() {
        @Override public void addShipExplosion(float x, float y, Color color, float angle,
                                               Vector2 velocity, boolean slowFragments) {
            pendingExplosions.add(new TcpProtocol.SnapshotExplosion(
                true,
                x,
                y,
                color.r,
                color.g,
                color.b,
                color.a,
                angle + 90f,
                velocity.x,
                velocity.y,
                0f,
                slowFragments));
        }
    };

    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private Thread tickThread;
    private int nextPlayerId = 1;
    private long tick;
    private float elapsedTime;
    private float nextAsteroidAppearanceTime = ASTEROID_APPEARANCE_TIME;
    private float nextStarMassIncreaseTime = STAR_APPEARANCE_TIME + STAR_MASS_INTERVAL;
    private Star star;

    private LocalTcpGameServer(String bindHost, int port) {
        this.bindHost = bindHost == null ? "" : bindHost.trim();
        this.port = port;
    }

    private void start() throws IOException {
        serverSocket = new ServerSocket();
        if (bindHost.isEmpty() || "0.0.0.0".equals(bindHost) || "*".equals(bindHost)) {
            serverSocket.bind(new InetSocketAddress(port));
        } else {
            serverSocket.bind(new InetSocketAddress(bindHost, port));
        }
        synchronized (players) {
            players.clear();
            asteroids.clear();
            pendingExplosions.clear();
            tick = 0L;
            elapsedTime = 0f;
            nextAsteroidAppearanceTime = ASTEROID_APPEARANCE_TIME;
            nextStarMassIncreaseTime = STAR_APPEARANCE_TIME + STAR_MASS_INTERVAL;
            activeShipCollisions.clear();
            star = null;
        }
        running = true;

        acceptThread = new Thread(new Runnable() {
            @Override public void run() {
                acceptLoop();
            }
        }, "marcianos-local-server-accept-" + port);
        acceptThread.setDaemon(true);
        acceptThread.start();

        tickThread = new Thread(new Runnable() {
            @Override public void run() {
                tickLoop();
            }
        }, "marcianos-local-server-tick-" + port);
        tickThread.setDaemon(true);
        tickThread.start();
    }

    private void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        synchronized (clients) {
            for (ClientConnection c : clients) c.close();
            clients.clear();
        }
        synchronized (players) {
            players.clear();
            asteroids.clear();
            pendingExplosions.clear();
            activeShipCollisions.clear();
            star = null;
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                ClientConnection client = new ClientConnection(socket);
                clients.add(client);
                client.startReader();
            } catch (SocketException ex) {
                if (!running) return;
            } catch (IOException ignored) {
            }
        }
    }

    private void tickLoop() {
        final long sleepMs = 50L;
        final float delta = 1f / 20f;
        while (running) {
            long start = System.currentTimeMillis();
            tick++;
            updateWorld(delta);
            broadcastSnapshot();
            long elapsed = System.currentTimeMillis() - start;
            long wait = sleepMs - elapsed;
            if (wait > 0L) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private void updateWorld(float delta) {
        synchronized (players) {
            elapsedTime += delta;
            if (elapsedTime >= nextAsteroidAppearanceTime) {
                asteroids.add(new Asteroid());
                nextAsteroidAppearanceTime += ASTEROID_SPAWN_INTERVAL;
            }
            if (star == null && elapsedTime >= STAR_APPEARANCE_TIME) {
                star = new Star(marcianos.GameScreen.WORLD_WIDTH / 2f, marcianos.GameScreen.WORLD_HEIGHT / 2f);
            }
            if (star != null) {
                while (elapsedTime >= nextStarMassIncreaseTime) {
                    star.increaseMass();
                    nextStarMassIncreaseTime += STAR_MASS_INTERVAL;
                }
            }

            List<ServerPlayerState> orderedPlayers = orderedPlayers();
            for (ServerPlayerState serverPlayer : orderedPlayers) {
                serverPlayer.player.setExternalInput(toInputCommand(serverPlayer.input));
                serverPlayer.player.update(delta, null);
            }
            if (star != null) {
                for (ServerPlayerState serverPlayer : orderedPlayers) {
                    if (serverPlayer.player.isAlive()) star.applyGravity(serverPlayer.player, delta);
                }
            }
            for (Asteroid asteroid : asteroids) asteroid.update(delta);

            checkPlayerCollision(orderedPlayers);
            for (ServerPlayerState shooter : orderedPlayers) {
                checkBulletPlayerCollisions(shooter, orderedPlayers);
            }
            for (ServerPlayerState shooter : orderedPlayers) {
                checkAsteroidCollisions(shooter);
            }
            for (ServerPlayerState serverPlayer : orderedPlayers) {
                checkAsteroidPlayerCollisions(serverPlayer);
                checkStarPlayerCollisions(serverPlayer);
            }
            for (ServerPlayerState serverPlayer : orderedPlayers) {
                if (!serverPlayer.player.isAlive() && serverPlayer.player.getLives() == 0) {
                    serverPlayer.player.resetForOnlineRespawn(4);
                    serverPlayer.player.setExternalInput(InputCommand.none());
                    serverPlayer.input = TcpProtocol.emptyInput();
                    serverPlayer.touchingStar = false;
                }
            }
        }
    }

    private List<ServerPlayerState> orderedPlayers() {
        ArrayList<ServerPlayerState> ordered = new ArrayList<>(players.values());
        ordered.sort(Comparator.comparingInt(new java.util.function.ToIntFunction<ServerPlayerState>() {
            @Override public int applyAsInt(ServerPlayerState value) {
                return value.playerId;
            }
        }));
        return ordered;
    }

    private static InputCommand toInputCommand(TcpProtocol.ParsedInput input) {
        if (input == null) return InputCommand.none();
        return new InputCommand(
            input.rotateLeft,
            input.rotateRight,
            input.thrust,
            input.fire,
            input.shield,
            input.hyperspace);
    }

    private void checkPlayerCollision(List<ServerPlayerState> orderedPlayers) {
        Set<Long> newCollisions = new HashSet<>();
        for (int i = 0; i < orderedPlayers.size(); i++) {
            ServerPlayerState stateOne = orderedPlayers.get(i);
            PlayerManager playerOne = stateOne.player;
            if (!playerOne.isAlive()) continue;
            for (int j = i + 1; j < orderedPlayers.size(); j++) {
                ServerPlayerState stateTwo = orderedPlayers.get(j);
                PlayerManager playerTwo = stateTwo.player;
                if (!playerTwo.isAlive()) continue;
                if (playerOne.getPosition().dst(playerTwo.getPosition()) < SHIP_COLLISION_DISTANCE) {
                    long pairKey = collisionPairKey(stateOne.playerId, stateTwo.playerId);
                    newCollisions.add(pairKey);
                    if (!activeShipCollisions.contains(pairKey)) {
                        adjustScore(stateOne, -1);
                        adjustScore(stateTwo, -1);
                        playerOne.hit(playerTwo.getPosition().x, playerTwo.getPosition().y, 16f, explosionSink);
                        playerTwo.hit(playerOne.getPosition().x, playerOne.getPosition().y, 16f, explosionSink);
                    }
                }
            }
        }
        activeShipCollisions.clear();
        activeShipCollisions.addAll(newCollisions);
    }

    private void checkBulletPlayerCollisions(ServerPlayerState shooterState,
                                             List<ServerPlayerState> orderedPlayers) {
        PlayerManager shooter = shooterState.player;
        for (int i = shooter.getBullets().size - 1; i >= 0; i--) {
            Bullet bullet = shooter.getBullets().get(i);
            for (ServerPlayerState targetState : orderedPlayers) {
                if (targetState.playerId == shooterState.playerId) continue;
                PlayerManager target = targetState.player;
                if (target.isAlive() && bullet.getPosition().dst(target.getPosition()) < BULLET_HIT_DISTANCE) {
                    boolean killed = target.hit(target.getPosition().x, target.getPosition().y, 2f, explosionSink);
                    adjustScore(targetState, -1);
                    if (killed) adjustScore(shooterState, 1);
                    shooter.destroyBullet(bullet);
                    break;
                }
            }
        }
    }

    private void checkAsteroidCollisions(ServerPlayerState shooterState) {
        PlayerManager shooter = shooterState.player;
        for (int bulletIndex = shooter.getBullets().size - 1; bulletIndex >= 0; bulletIndex--) {
            Bullet bullet = shooter.getBullets().get(bulletIndex);
            for (int asteroidIndex = asteroids.size() - 1; asteroidIndex >= 0; asteroidIndex--) {
                Asteroid asteroid = asteroids.get(asteroidIndex);
                if (bullet.getPosition().dst(asteroid.getPosition()) <= asteroid.getRadius() + 3f) {
                    shooter.destroyBullet(bullet);
                    replaceAsteroid(asteroidIndex, asteroid);
                    break;
                }
            }
        }
    }

    private void checkAsteroidPlayerCollisions(ServerPlayerState serverPlayer) {
        PlayerManager player = serverPlayer.player;
        if (!player.isAlive()) return;
        for (int asteroidIndex = asteroids.size() - 1; asteroidIndex >= 0; asteroidIndex--) {
            Asteroid asteroid = asteroids.get(asteroidIndex);
            float collisionDistance = asteroid.getRadius() + 16f;
            if (player.getPosition().dst(asteroid.getPosition()) <= collisionDistance) {
                player.hit(asteroid.getPosition().x, asteroid.getPosition().y,
                    asteroid.getRadius(), explosionSink);
                adjustScore(serverPlayer, -1);
                replaceAsteroid(asteroidIndex, asteroid);
            }
        }
    }

    private void checkStarPlayerCollisions(ServerPlayerState serverPlayer) {
        if (star == null) {
            serverPlayer.touchingStar = false;
            return;
        }
        PlayerManager player = serverPlayer.player;
        if (!player.isAlive()) {
            serverPlayer.touchingStar = false;
            return;
        }
        float collisionDistance = star.getRadius() + 16f;
        boolean colliding = player.getPosition().dst(star.getX(), star.getY()) <= collisionDistance;
        if (colliding && !serverPlayer.touchingStar) {
            player.hit(star.getX(), star.getY(), star.getRadius(), explosionSink, true);
            adjustScore(serverPlayer, -1);
        }
        serverPlayer.touchingStar = colliding;
    }

    private static long collisionPairKey(int playerOneId, int playerTwoId) {
        int low = Math.min(playerOneId, playerTwoId);
        int high = Math.max(playerOneId, playerTwoId);
        return (((long) low) << 32) | (high & 0xffffffffL);
    }

    private static void adjustScore(ServerPlayerState state, int delta) {
        state.score += delta;
    }

    private void replaceAsteroid(int index, Asteroid asteroid) {
        asteroids.remove(index);
        addAsteroidExplosion(asteroid);
        for (Asteroid child : asteroid.split()) asteroids.add(child);
    }

    private void addAsteroidExplosion(Asteroid asteroid) {
        Vector2 position = asteroid.getPosition();
        Vector2 velocity = asteroid.getVelocity();
        pendingExplosions.add(new TcpProtocol.SnapshotExplosion(
            false,
            position.x,
            position.y,
            Color.GRAY.r,
            Color.GRAY.g,
            Color.GRAY.b,
            Color.GRAY.a,
            asteroid.getRotation(),
            velocity.x,
            velocity.y,
            asteroid.getRadius(),
            false));
    }

    private void broadcastSnapshot() {
        List<TcpProtocol.SnapshotPlayer> frame = new ArrayList<>();
        List<TcpProtocol.SnapshotBullet> bullets = new ArrayList<>();
        List<TcpProtocol.SnapshotAsteroid> asteroidFrame = new ArrayList<>();
        List<TcpProtocol.SnapshotExplosion> explosionFrame = new ArrayList<>();

        synchronized (players) {
            List<ServerPlayerState> orderedPlayers = orderedPlayers();
            for (ServerPlayerState p : orderedPlayers) {
                PlayerManager player = p.player;
                Vector2 position = player.getPosition();
                Vector2 velocity = player.getVelocity();
                frame.add(new TcpProtocol.SnapshotPlayer(
                    p.playerId,
                    position.x,
                    position.y,
                    player.getAngle(),
                    player.getShield(),
                    player.getLives(),
                    player.isShieldActive(),
                    player.isAlive(),
                    velocity.x,
                    velocity.y,
                    player.getHyperspaceAttempts(),
                    p.playerName,
                    p.score));
                for (Bullet bullet : player.getBullets()) {
                    bullets.add(new TcpProtocol.SnapshotBullet(
                        p.playerId,
                        bullet.getPosition().x,
                        bullet.getPosition().y));
                }
            }
            for (Asteroid asteroid : asteroids) {
                asteroidFrame.add(new TcpProtocol.SnapshotAsteroid(
                    asteroid.getPosition().x,
                    asteroid.getPosition().y,
                    asteroid.getRadius(),
                    asteroid.getRotation(),
                    asteroid.getVertices()));
            }
            explosionFrame.addAll(pendingExplosions);
            pendingExplosions.clear();
        }

        TcpProtocol.SnapshotStar starFrame = null;
        synchronized (players) {
            if (star != null) {
                starFrame = new TcpProtocol.SnapshotStar(star.getX(), star.getY(), star.getRadius());
            }
        }

        String line = TcpProtocol.snapshotMessage(tick, frame, bullets, asteroidFrame, explosionFrame, starFrame);
        synchronized (clients) {
            for (ClientConnection client : clients) client.send(line);
        }
    }

    private final class ClientConnection {
        private final Socket socket;
        private final PrintWriter writer;
        private final BufferedReader reader;
        private Thread readerThread;
        private int playerId = -1;

        private ClientConnection(Socket socket) throws IOException {
            this.socket = socket;
            this.writer = new PrintWriter(socket.getOutputStream(), true);
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        }

        private void startReader() {
            readerThread = new Thread(new Runnable() {
                @Override public void run() {
                    readLoop();
                }
            }, "marcianos-local-server-client-" + socket.getPort());
            readerThread.setDaemon(true);
            readerThread.start();
        }

        private void readLoop() {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    if (line.startsWith(TcpProtocol.JOIN + "|")) {
                        handleJoin(line);
                    } else if (line.startsWith(TcpProtocol.INPUT + "|")) {
                        handleInput(line);
                    } else if (TcpProtocol.RESPAWN.equals(line)) {
                        handleRespawn();
                    }
                }
            } catch (IOException ignored) {
            } finally {
                close();
                clients.remove(this);
                if (playerId > 0) {
                    synchronized (players) {
                        players.remove(playerId);
                    }
                }
            }
        }

        private void handleJoin(String line) {
            if (playerId > 0) return;
            String playerName = TcpProtocol.parseJoinName(line);
            synchronized (players) {
                if (players.size() >= MAX_PLAYERS) {
                    send(TcpProtocol.errorMessage("Server full"));
                    close();
                    return;
                }
                playerId = nextPlayerId++;
                players.put(playerId, new ServerPlayerState(playerId, playerName));
            }
            send(TcpProtocol.welcomeMessage(playerId));
        }

        private void handleInput(String line) {
            if (playerId <= 0) return;
            TcpProtocol.ParsedInput input = TcpProtocol.parseInput(line);
            if (input == null) return;
            synchronized (players) {
                ServerPlayerState state = players.get(playerId);
                if (state != null) state.input = input;
            }
        }

        private void handleRespawn() {
            if (playerId <= 0) return;
            synchronized (players) {
                ServerPlayerState state = players.get(playerId);
                if (state == null) return;
                state.player.resetForOnlineRespawn(4);
                state.player.setExternalInput(InputCommand.none());
                state.input = TcpProtocol.emptyInput();
                state.touchingStar = false;
            }
        }

        private void send(String line) {
            synchronized (writer) {
                writer.println(line);
            }
        }

        private void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static final class ServerPlayerState {
        private final int playerId;
        private final String playerName;
        private final PlayerManager player;
        private TcpProtocol.ParsedInput input = TcpProtocol.emptyInput();
        private int score;
        private boolean touchingStar;

        private ServerPlayerState(int playerId, String playerName) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.player = new PlayerManager(colorFor(playerId), 0, 0, 0, 0, 0, 0, 4);
            this.player.setExternalInput(InputCommand.none());
            this.score = 0;
            this.touchingStar = false;
        }

        private static Color colorFor(int playerId) {
            if (playerId == 1) return Color.WHITE;
            if (playerId == 2) return Color.YELLOW;
            float hueBand = (playerId % 6) / 6f;
            float r = 0.35f + 0.55f * ((float) Math.sin(hueBand * Math.PI * 2.0 + 0.0f)) * 0.5f + 0.28f;
            float g = 0.35f + 0.55f * ((float) Math.sin(hueBand * Math.PI * 2.0 + 2.1f)) * 0.5f + 0.28f;
            float b = 0.35f + 0.55f * ((float) Math.sin(hueBand * Math.PI * 2.0 + 4.2f)) * 0.5f + 0.28f;
            return new Color(clamp01(r), clamp01(g), clamp01(b), 1f);
        }

        private static float clamp01(float value) {
            return Math.max(0f, Math.min(1f, value));
        }
    }
}
