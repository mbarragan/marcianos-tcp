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
        private final boolean shieldActive;
        private final boolean localPlayer;

        public PlayerState(int playerId, float x, float y, float angle, float shield,
                           boolean shieldActive, boolean localPlayer) {
            this.playerId = playerId;
            this.x = x;
            this.y = y;
            this.angle = angle;
            this.shield = shield;
            this.shieldActive = shieldActive;
            this.localPlayer = localPlayer;
        }

        public int playerId() { return playerId; }
        public float x() { return x; }
        public float y() { return y; }
        public float angle() { return angle; }
        public float shield() { return shield; }
        public boolean shieldActive() { return shieldActive; }
        public boolean localPlayer() { return localPlayer; }
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

        public AsteroidState(float x, float y, float radius, float rotation) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.rotation = rotation;
        }

        public float x() { return x; }
        public float y() { return y; }
        public float radius() { return radius; }
        public float rotation() { return rotation; }
    }

    private final long tick;
    private final List<PlayerState> players;
    private final List<BulletState> bullets;
    private final List<AsteroidState> asteroids;

    public RemoteSnapshot(long tick, List<PlayerState> players,
                          List<BulletState> bullets, List<AsteroidState> asteroids) {
        this.tick = tick;
        this.players = Collections.unmodifiableList(new ArrayList<>(players));
        this.bullets = Collections.unmodifiableList(new ArrayList<>(bullets));
        this.asteroids = Collections.unmodifiableList(new ArrayList<>(asteroids));
    }

    public long tick() { return tick; }
    public List<PlayerState> players() { return players; }
    public List<BulletState> bullets() { return bullets; }
    public List<AsteroidState> asteroids() { return asteroids; }
}
