package marcianos.net;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Minimal world snapshot structure consumed by the online screen. */
public final class RemoteSnapshot {
    public static final class PlayerState {
        private final int playerId;
        private final float x;
        private final float y;
        private final float angle;
        private final float shield;
        private final int lives;
        private final boolean shieldActive;
        private final boolean alive;
        private final float vx;
        private final float vy;
        private final int hyperspaceAttempts;
        private final String playerName;
        private final boolean localPlayer;

        public PlayerState(int playerId, float x, float y, float angle, float shield,
                           int lives, boolean shieldActive, boolean alive,
               float vx, float vy, int hyperspaceAttempts, String playerName,
                   boolean localPlayer) {
            this.playerId = playerId;
            this.x = x;
            this.y = y;
            this.angle = angle;
            this.shield = shield;
            this.lives = lives;
            this.shieldActive = shieldActive;
            this.alive = alive;
            this.vx = vx;
            this.vy = vy;
            this.hyperspaceAttempts = hyperspaceAttempts;
            this.playerName = playerName;
            this.localPlayer = localPlayer;
        }

        public int playerId() { return playerId; }
        public float x() { return x; }
        public float y() { return y; }
        public float angle() { return angle; }
        public float shield() { return shield; }
        public int lives() { return lives; }
        public boolean shieldActive() { return shieldActive; }
        public boolean alive() { return alive; }
        public float vx() { return vx; }
        public float vy() { return vy; }
        public int hyperspaceAttempts() { return hyperspaceAttempts; }
        public String playerName() { return playerName; }
        public boolean localPlayer() { return localPlayer; }
    }

    public static final class ExplosionState {
        private final boolean shipExplosion;
        private final float x;
        private final float y;
        private final float r;
        private final float g;
        private final float b;
        private final float a;
        private final float angle;
        private final float vx;
        private final float vy;
        private final float radius;
        private final boolean slowFragments;

        public ExplosionState(boolean shipExplosion, float x, float y,
                              float r, float g, float b, float a,
                              float angle, float vx, float vy,
                              float radius, boolean slowFragments) {
            this.shipExplosion = shipExplosion;
            this.x = x;
            this.y = y;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            this.angle = angle;
            this.vx = vx;
            this.vy = vy;
            this.radius = radius;
            this.slowFragments = slowFragments;
        }

        public boolean shipExplosion() { return shipExplosion; }
        public float x() { return x; }
        public float y() { return y; }
        public float r() { return r; }
        public float g() { return g; }
        public float b() { return b; }
        public float a() { return a; }
        public float angle() { return angle; }
        public float vx() { return vx; }
        public float vy() { return vy; }
        public float radius() { return radius; }
        public boolean slowFragments() { return slowFragments; }
    }

    public static final class BulletState {
        private final int ownerPlayerId;
        private final float x;
        private final float y;

        public BulletState(int ownerPlayerId, float x, float y) {
            this.ownerPlayerId = ownerPlayerId;
            this.x = x;
            this.y = y;
        }

        public int ownerPlayerId() { return ownerPlayerId; }
        public float x() { return x; }
        public float y() { return y; }
    }

    public static final class AsteroidState {
        private final float x;
        private final float y;
        private final float radius;
        private final float rotation;
        private final float[] vertices;

        public AsteroidState(float x, float y, float radius, float rotation) {
            this(x, y, radius, rotation, null);
        }

        public AsteroidState(float x, float y, float radius, float rotation, float[] vertices) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.rotation = rotation;
            this.vertices = vertices == null ? null : vertices.clone();
        }

        public float x() { return x; }
        public float y() { return y; }
        public float radius() { return radius; }
        public float rotation() { return rotation; }
        public float[] vertices() { return vertices == null ? null : vertices.clone(); }
    }

    private final long tick;
    private final List<PlayerState> players;
    private final List<BulletState> bullets;
    private final List<AsteroidState> asteroids;
    private final List<ExplosionState> explosions;

    public RemoteSnapshot(long tick, List<PlayerState> players,
                          List<BulletState> bullets, List<AsteroidState> asteroids,
                          List<ExplosionState> explosions) {
        this.tick = tick;
        this.players = Collections.unmodifiableList(new ArrayList<>(players));
        this.bullets = Collections.unmodifiableList(new ArrayList<>(bullets));
        this.asteroids = Collections.unmodifiableList(new ArrayList<>(asteroids));
        this.explosions = Collections.unmodifiableList(new ArrayList<>(explosions));
    }

    public long tick() { return tick; }
    public List<PlayerState> players() { return players; }
    public List<BulletState> bullets() { return bullets; }
    public List<AsteroidState> asteroids() { return asteroids; }
    public List<ExplosionState> explosions() { return explosions; }
}
