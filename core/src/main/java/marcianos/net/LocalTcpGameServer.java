package marcianos.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Lightweight localhost TCP server used to validate the online client flow. */
public final class LocalTcpGameServer {
    private static final int MAX_PLAYERS = 16;
    private static final float WORLD_WIDTH = 1710f;
    private static final float WORLD_HEIGHT = 1000f;
    private static final float PLAYER_RADIUS = 16f;
    private static final float SHIP_COLLISION_DISTANCE = 32f;
    private static final float BULLET_HIT_DISTANCE = 18f;
    private static final float BULLET_SPEED = 8f * 60f;
    private static final float BULLET_LIFE = 4.2f;
    private static final float ASTEROID_MIN_RADIUS = 14f;
    private static final float ASTEROID_MAX_RADIUS = 44f;
    private static final float ASTEROID_SMALL_MAX_RADIUS = 23f;
    private static final float ASTEROID_MEDIUM_MIN_RADIUS = 24f;
    private static final float ASTEROID_MEDIUM_MAX_RADIUS = 33f;
    private static final float ASTEROID_MIN_SPEED = 20f;
    private static final float ASTEROID_MAX_SPEED = 45f;
    private static final float ASTEROID_SPAWN_INTERVAL = 20f;
    private static final Map<Integer, LocalTcpGameServer> RUNNING = new ConcurrentHashMap<>();

    public static synchronized void startIfNeeded(int port) throws IOException {
        if (RUNNING.containsKey(port)) return;
        LocalTcpGameServer server = new LocalTcpGameServer(port);
        server.start();
        RUNNING.put(port, server);
    }

    public static synchronized void stopIfRunning(int port) {
        LocalTcpGameServer server = RUNNING.remove(port);
        if (server != null) server.stop();
    }

    private final int port;
    private final List<ClientConnection> clients = Collections.synchronizedList(new ArrayList<ClientConnection>());
    private final Map<Integer, ServerPlayerState> players = new HashMap<>();
    private final List<ServerAsteroidState> asteroids = new ArrayList<>();

    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private Thread tickThread;
    private int nextPlayerId = 1;
    private long tick;
    private float asteroidSpawnTimer;

    private LocalTcpGameServer(int port) {
        this.port = port;
    }

    private void start() throws IOException {
        serverSocket = new ServerSocket(port);
        synchronized (players) {
            asteroids.clear();
            for (int i = 0; i < 10; i++) asteroids.add(ServerAsteroidState.large());
            for (int i = 0; i < 5; i++) asteroids.add(ServerAsteroidState.medium());
        }
        asteroidSpawnTimer = ASTEROID_SPAWN_INTERVAL;
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
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private void updateWorld(float delta) {
        synchronized (players) {
            for (ServerPlayerState p : players.values()) {
                if (p.input.rotateLeft) p.angle += 140f * delta;
                if (p.input.rotateRight) p.angle -= 140f * delta;
                if (p.input.thrust) {
                    float heading = p.angle + 90f;
                    p.vx += (float) Math.cos(Math.toRadians(heading)) * 60f * delta;
                    p.vy += (float) Math.sin(Math.toRadians(heading)) * 60f * delta;
                }
                p.x += p.vx * delta;
                p.y += p.vy * delta;
                if (p.input.shield) p.shield = Math.max(0f, p.shield - 25f * delta);
                else p.shield = Math.min(100f, p.shield + 8f * delta);
                p.fireCooldown -= delta;
                if (p.input.fire && p.fireCooldown <= 0f && p.bullets.size() < 3) {
                    float heading = p.angle + 90f;
                    float cos = (float) Math.cos(Math.toRadians(heading));
                    float sin = (float) Math.sin(Math.toRadians(heading));
                    p.bullets.add(new ServerBulletState(
                        p.playerId,
                        p.x + cos * 20f,
                        p.y + sin * 20f,
                        cos * BULLET_SPEED,
                        sin * BULLET_SPEED,
                        BULLET_LIFE));
                    p.fireCooldown = .22f;
                }
                wrap(p);
            }

            for (ServerPlayerState p : players.values()) {
                for (int i = p.bullets.size() - 1; i >= 0; i--) {
                    ServerBulletState bullet = p.bullets.get(i);
                    bullet.x += bullet.vx * delta;
                    bullet.y += bullet.vy * delta;
                    bullet.life -= delta;
                    wrap(bullet);
                    if (bullet.life <= 0f) p.bullets.remove(i);
                }
            }

            for (ServerAsteroidState asteroid : asteroids) {
                asteroid.x += asteroid.vx * delta;
                asteroid.y += asteroid.vy * delta;
                asteroid.rotation += 25f * delta;
                wrap(asteroid);
            }

            asteroidSpawnTimer -= delta;
            if (asteroidSpawnTimer <= 0f) {
                asteroids.add(ServerAsteroidState.random());
                asteroidSpawnTimer += ASTEROID_SPAWN_INTERVAL;
            }

            checkShipCollisions();
            checkBulletPlayerCollisions();
            checkBulletAsteroidCollisions();
            checkAsteroidPlayerCollisions();
        }
    }

    private static void wrap(ServerPlayerState p) {
        if (p.x < -20f) p.x = WORLD_WIDTH + 20f;
        if (p.x > WORLD_WIDTH + 20f) p.x = -20f;
        if (p.y < -20f) p.y = WORLD_HEIGHT + 20f;
        if (p.y > WORLD_HEIGHT + 20f) p.y = -20f;
    }

    private static void wrap(ServerBulletState b) {
        if (b.x < -2f) b.x = WORLD_WIDTH + 2f;
        if (b.x > WORLD_WIDTH + 2f) b.x = -2f;
        if (b.y < -2f) b.y = WORLD_HEIGHT + 2f;
        if (b.y > WORLD_HEIGHT + 2f) b.y = -2f;
    }

    private static void wrap(ServerAsteroidState a) {
        if (a.x < -a.radius) a.x = WORLD_WIDTH + a.radius;
        if (a.x > WORLD_WIDTH + a.radius) a.x = -a.radius;
        if (a.y < -a.radius) a.y = WORLD_HEIGHT + a.radius;
        if (a.y > WORLD_HEIGHT + a.radius) a.y = -a.radius;
    }

    private void checkShipCollisions() {
        List<ServerPlayerState> allPlayers = new ArrayList<>(players.values());
        for (int i = 0; i < allPlayers.size(); i++) {
            ServerPlayerState a = allPlayers.get(i);
            for (int j = i + 1; j < allPlayers.size(); j++) {
                ServerPlayerState b = allPlayers.get(j);
                float dx = a.x - b.x;
                float dy = a.y - b.y;
                if (dx * dx + dy * dy <= SHIP_COLLISION_DISTANCE * SHIP_COLLISION_DISTANCE) {
                    applyShipHit(a);
                    applyShipHit(b);
                }
            }
        }
    }

    private void checkBulletPlayerCollisions() {
        List<ServerPlayerState> allPlayers = new ArrayList<>(players.values());
        for (ServerPlayerState shooter : allPlayers) {
            for (int bulletIndex = shooter.bullets.size() - 1; bulletIndex >= 0; bulletIndex--) {
                ServerBulletState bullet = shooter.bullets.get(bulletIndex);
                boolean consumed = false;
                for (ServerPlayerState target : allPlayers) {
                    if (target.playerId == shooter.playerId) continue;
                    float dx = bullet.x - target.x;
                    float dy = bullet.y - target.y;
                    if (dx * dx + dy * dy <= BULLET_HIT_DISTANCE * BULLET_HIT_DISTANCE) {
                        applyShipHit(target);
                        shooter.bullets.remove(bulletIndex);
                        consumed = true;
                        break;
                    }
                }
                if (consumed) continue;
            }
        }
    }

    private void checkBulletAsteroidCollisions() {
        for (ServerPlayerState shooter : players.values()) {
            for (int bulletIndex = shooter.bullets.size() - 1; bulletIndex >= 0; bulletIndex--) {
                ServerBulletState bullet = shooter.bullets.get(bulletIndex);
                boolean consumed = false;
                for (int asteroidIndex = asteroids.size() - 1; asteroidIndex >= 0; asteroidIndex--) {
                    ServerAsteroidState asteroid = asteroids.get(asteroidIndex);
                    float hitDistance = asteroid.radius + 3f;
                    float dx = bullet.x - asteroid.x;
                    float dy = bullet.y - asteroid.y;
                    if (dx * dx + dy * dy <= hitDistance * hitDistance) {
                        shooter.bullets.remove(bulletIndex);
                        asteroids.remove(asteroidIndex);
                        asteroids.addAll(asteroid.split());
                        consumed = true;
                        break;
                    }
                }
                if (consumed) continue;
            }
        }
    }

    private void checkAsteroidPlayerCollisions() {
        List<ServerPlayerState> allPlayers = new ArrayList<>(players.values());
        for (int asteroidIndex = asteroids.size() - 1; asteroidIndex >= 0; asteroidIndex--) {
            ServerAsteroidState asteroid = asteroids.get(asteroidIndex);
            boolean collided = false;
            for (ServerPlayerState player : allPlayers) {
                float collisionDistance = asteroid.radius + PLAYER_RADIUS;
                float dx = player.x - asteroid.x;
                float dy = player.y - asteroid.y;
                if (dx * dx + dy * dy <= collisionDistance * collisionDistance) {
                    applyShipHit(player);
                    collided = true;
                    break;
                }
            }
            if (collided) {
                asteroids.remove(asteroidIndex);
                asteroids.addAll(asteroid.split());
            }
        }
    }

    private void applyShipHit(ServerPlayerState player) {
        if (player.input.shield && player.shield > 0f) {
            player.shield = Math.max(0f, player.shield - 20f);
            return;
        }
        respawn(player);
    }

    private void respawn(ServerPlayerState player) {
        player.x = randomRange(80f, WORLD_WIDTH - 80f);
        player.y = randomRange(100f, WORLD_HEIGHT - 80f);
        player.vx = 0f;
        player.vy = 0f;
        player.angle = randomRange(0f, 360f);
        player.shield = 100f;
        player.bullets.clear();
    }

    private void broadcastSnapshot() {
        List<TcpProtocol.SnapshotPlayer> frame = new ArrayList<>();
        List<TcpProtocol.SnapshotBullet> bullets = new ArrayList<>();
        List<TcpProtocol.SnapshotAsteroid> asteroidFrame = new ArrayList<>();
        synchronized (players) {
            for (ServerPlayerState p : players.values()) {
                frame.add(new TcpProtocol.SnapshotPlayer(
                    p.playerId, p.x, p.y, p.angle, p.shield, p.input.shield && p.shield > 0f));
                for (ServerBulletState bullet : p.bullets) {
                    bullets.add(new TcpProtocol.SnapshotBullet(bullet.ownerPlayerId, bullet.x, bullet.y));
                }
            }
            for (ServerAsteroidState asteroid : asteroids) {
                asteroidFrame.add(new TcpProtocol.SnapshotAsteroid(
                    asteroid.x, asteroid.y, asteroid.radius, asteroid.rotation));
            }
        }
        String line = TcpProtocol.snapshotMessage(tick, frame, bullets, asteroidFrame);
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
            synchronized (players) {
                if (players.size() >= MAX_PLAYERS) {
                    send(TcpProtocol.errorMessage("Server full"));
                    close();
                    return;
                }
                playerId = nextPlayerId++;
                ServerPlayerState state = new ServerPlayerState(playerId);
                players.put(playerId, state);
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
        private float x;
        private float y;
        private float angle;
        private float vx;
        private float vy;
        private float fireCooldown;
        private float shield = 100f;
        private final List<ServerBulletState> bullets = new ArrayList<>();
        private TcpProtocol.ParsedInput input = TcpProtocol.emptyInput();

        private ServerPlayerState(int playerId) {
            this.playerId = playerId;
            this.x = 120f + (playerId % 8) * 180f;
            this.y = 140f + (playerId / 8) * 280f;
            this.angle = 90f;
        }
    }

    private static final class ServerBulletState {
        private final int ownerPlayerId;
        private float x;
        private float y;
        private final float vx;
        private final float vy;
        private float life;

        private ServerBulletState(int ownerPlayerId, float x, float y, float vx, float vy, float life) {
            this.ownerPlayerId = ownerPlayerId;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
        }
    }

    private static final class ServerAsteroidState {
        private final float radius;
        private float x;
        private float y;
        private final float vx;
        private final float vy;
        private float rotation;

        private ServerAsteroidState(float radius, float x, float y, float vx, float vy, float rotation) {
            this.radius = radius;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.rotation = rotation;
        }

        private static ServerAsteroidState random() {
            return withRadius(randomRange(ASTEROID_MIN_RADIUS, ASTEROID_MAX_RADIUS));
        }

        private static ServerAsteroidState large() {
            return withRadius(randomRange(34f, ASTEROID_MAX_RADIUS));
        }

        private static ServerAsteroidState medium() {
            return withRadius(randomRange(ASTEROID_MEDIUM_MIN_RADIUS, ASTEROID_MEDIUM_MAX_RADIUS));
        }

        private static ServerAsteroidState withRadius(float radius) {
            float heading = randomRange(0f, 360f);
            float speed = randomRange(ASTEROID_MIN_SPEED, ASTEROID_MAX_SPEED);
            return new ServerAsteroidState(
                radius,
                randomRange(0f, WORLD_WIDTH),
                randomRange(0f, WORLD_HEIGHT),
                (float) Math.cos(Math.toRadians(heading)) * speed,
                (float) Math.sin(Math.toRadians(heading)) * speed,
                randomRange(0f, 360f));
        }

        private List<ServerAsteroidState> split() {
            List<ServerAsteroidState> children = new ArrayList<>(2);
            if (radius <= ASTEROID_SMALL_MAX_RADIUS) return children;

            float childMin = radius <= ASTEROID_MEDIUM_MAX_RADIUS
                ? ASTEROID_MIN_RADIUS : ASTEROID_MEDIUM_MIN_RADIUS;
            float childMax = radius <= ASTEROID_MEDIUM_MAX_RADIUS
                ? ASTEROID_SMALL_MAX_RADIUS : ASTEROID_MEDIUM_MAX_RADIUS;
            float radiusOne = randomRange(childMin, childMax);
            float radiusTwo = randomRange(childMin, childMax);
            float massOne = radiusOne * radiusOne;
            float massTwo = radiusTwo * radiusTwo;
            float totalMass = massOne + massTwo;

            float separationAngle = randomRange(0f, 360f);
            float sepX = (float) Math.cos(Math.toRadians(separationAngle));
            float sepY = (float) Math.sin(Math.toRadians(separationAngle));
            float separationSpeed = randomRange(8f, 16f);
            float impulseOne = separationSpeed * massTwo / totalMass;
            float impulseTwo = separationSpeed * massOne / totalMass;

            children.add(new ServerAsteroidState(
                radiusOne,
                x + sepX * radiusOne,
                y + sepY * radiusOne,
                vx + sepX * impulseOne,
                vy + sepY * impulseOne,
                rotation + randomRange(-20f, 20f)));
            children.add(new ServerAsteroidState(
                radiusTwo,
                x - sepX * radiusTwo,
                y - sepY * radiusTwo,
                vx - sepX * impulseTwo,
                vy - sepY * impulseTwo,
                rotation + randomRange(-20f, 20f)));
            return children;
        }
    }

    private static float randomRange(float min, float max) {
        return min + (float) Math.random() * (max - min);
    }
}
