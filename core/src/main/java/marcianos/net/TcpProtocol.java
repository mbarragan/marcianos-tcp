package marcianos.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import marcianos.InputCommand;

/** Line-based text protocol used by the local TCP implementation. */
public final class TcpProtocol {
    public static final String JOIN = "JOIN";
    public static final String INPUT = "INPUT";
    public static final String RESPAWN = "RESPAWN";
    public static final String WELCOME = "WELCOME";
    public static final String SNAP = "SNAP";
    public static final String ERROR = "ERROR";

    private TcpProtocol() {
    }

    public static String joinMessage(String playerName) {
        String safe = playerName == null ? "Player" : playerName.trim();
        if (safe.isEmpty()) safe = "Player";
        return JOIN + "|" + safe;
    }

    public static String parseJoinName(String line) {
        String[] parts = line.split("\\|", 2);
        if (parts.length != 2 || !JOIN.equals(parts[0])) return "Player";
        String safe = parts[1].trim();
        if (safe.isEmpty()) return "Player";
        return sanitizeName(safe);
    }

    public static String inputMessage(InputCommand input) {
        return INPUT + "|"
            + bit(input.rotateLeft()) + "|"
            + bit(input.rotateRight()) + "|"
            + bit(input.thrust()) + "|"
            + bit(input.fire()) + "|"
            + bit(input.shield()) + "|"
            + bit(input.hyperspace());
    }

    public static String respawnMessage() {
        return RESPAWN;
    }

    public static ParsedInput parseInput(String line) {
        String[] parts = line.split("\\|");
        if (parts.length != 7 || !INPUT.equals(parts[0])) return null;
        return new ParsedInput(
            parseBit(parts[1]), parseBit(parts[2]), parseBit(parts[3]),
            parseBit(parts[4]), parseBit(parts[5]), parseBit(parts[6])
        );
    }

    public static ParsedInput emptyInput() {
        return new ParsedInput(false, false, false, false, false, false);
    }

    public static int parseWelcomePlayerId(String line) {
        String[] parts = line.split("\\|");
        if (parts.length != 2 || !WELCOME.equals(parts[0])) return -1;
        try {
            return Integer.parseInt(parts[1]);
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    public static String parseError(String line) {
        if (!line.startsWith(ERROR + "|")) return null;
        return line.substring((ERROR + "|").length());
    }

    public static RemoteSnapshot parseSnapshot(String line, int localPlayerId) {
        String[] parts = line.split("\\|", 6);
        if (parts.length < 2 || !SNAP.equals(parts[0])) return null;

        long tick;
        try {
            tick = Long.parseLong(parts[1]);
        } catch (RuntimeException ex) {
            return null;
        }

        List<RemoteSnapshot.PlayerState> players = new ArrayList<>();
        if (parts.length >= 3 && !parts[2].trim().isEmpty()) {
            String[] entries = parts[2].split(";");
            for (String entry : entries) {
                String[] p = entry.split(",", 12);
                if (p.length < 5) continue;
                try {
                    int id = Integer.parseInt(p[0]);
                    float x = Float.parseFloat(p[1]);
                    float y = Float.parseFloat(p[2]);
                    float angle = Float.parseFloat(p[3]);
                    float shield = Float.parseFloat(p[4]);
                    int lives = 4;
                    boolean shieldActive = false;
                    boolean alive = true;
                    float vx = 0f;
                    float vy = 0f;
                    int hyperspaceAttempts = 0;
                    String playerName = "P" + id;
                    if (p.length == 6) {
                        // Legacy payload: id,x,y,angle,shield,shieldActive
                        shieldActive = parseBit(p[5]);
                    } else {
                        if (p.length >= 6) lives = Integer.parseInt(p[5]);
                        if (p.length >= 7) shieldActive = parseBit(p[6]);
                        if (p.length >= 8) alive = parseBit(p[7]);
                        else alive = lives > 0;
                        if (p.length >= 9) vx = Float.parseFloat(p[8]);
                        if (p.length >= 10) vy = Float.parseFloat(p[9]);
                        if (p.length >= 11) hyperspaceAttempts = Integer.parseInt(p[10]);
                        if (p.length >= 12 && !p[11].isEmpty()) playerName = p[11];
                    }
                    players.add(new RemoteSnapshot.PlayerState(
                        id, x, y, angle, shield, lives, shieldActive,
                        alive, vx, vy, hyperspaceAttempts, playerName, id == localPlayerId));
                } catch (RuntimeException ignored) {
                }
            }
        }

        List<RemoteSnapshot.BulletState> bullets = new ArrayList<>();
        if (parts.length >= 4 && !parts[3].trim().isEmpty()) {
            String[] entries = parts[3].split(";");
            for (String entry : entries) {
                String[] b = entry.split(",", 3);
                if (b.length != 3) continue;
                try {
                    int ownerPlayerId = Integer.parseInt(b[0]);
                    float x = Float.parseFloat(b[1]);
                    float y = Float.parseFloat(b[2]);
                    bullets.add(new RemoteSnapshot.BulletState(ownerPlayerId, x, y));
                } catch (RuntimeException ignored) {
                }
            }
        }

        List<RemoteSnapshot.AsteroidState> asteroids = new ArrayList<>();
        if (parts.length >= 5 && !parts[4].trim().isEmpty()) {
            String[] entries = parts[4].split(";");
            for (String entry : entries) {
                String[] a = entry.split(",");
                if (a.length < 4) continue;
                try {
                    float x = Float.parseFloat(a[0]);
                    float y = Float.parseFloat(a[1]);
                    float radius = Float.parseFloat(a[2]);
                    float rotation = Float.parseFloat(a[3]);
                    float[] vertices = null;
                    if (a.length >= 20) {
                        vertices = new float[16];
                        for (int i = 0; i < 16; i++) vertices[i] = Float.parseFloat(a[4 + i]);
                    }
                    asteroids.add(new RemoteSnapshot.AsteroidState(x, y, radius, rotation, vertices));
                } catch (RuntimeException ignored) {
                }
            }
        }

        List<RemoteSnapshot.ExplosionState> explosions = new ArrayList<>();
        if (parts.length >= 6 && !parts[5].trim().isEmpty()) {
            String[] entries = parts[5].split(";");
            for (String entry : entries) {
                String[] e = entry.split(",", 12);
                if (e.length != 12) continue;
                try {
                    boolean shipExplosion = "S".equals(e[0]);
                    float x = Float.parseFloat(e[1]);
                    float y = Float.parseFloat(e[2]);
                    float r = Float.parseFloat(e[3]);
                    float g = Float.parseFloat(e[4]);
                    float b = Float.parseFloat(e[5]);
                    float a = Float.parseFloat(e[6]);
                    float angle = Float.parseFloat(e[7]);
                    float vx = Float.parseFloat(e[8]);
                    float vy = Float.parseFloat(e[9]);
                    float radius = Float.parseFloat(e[10]);
                    boolean slowFragments = parseBit(e[11]);
                    explosions.add(new RemoteSnapshot.ExplosionState(
                        shipExplosion, x, y, r, g, b, a, angle, vx, vy, radius, slowFragments));
                } catch (RuntimeException ignored) {
                }
            }
        }

        return new RemoteSnapshot(tick, players, bullets, asteroids, explosions);
    }

    public static String snapshotMessage(long tick, List<SnapshotPlayer> players,
                                         List<SnapshotBullet> bullets,
                                         List<SnapshotAsteroid> asteroids,
                                         List<SnapshotExplosion> explosions) {
        StringBuilder sb = new StringBuilder();
        sb.append(SNAP).append('|').append(tick).append('|');
        for (int i = 0; i < players.size(); i++) {
            SnapshotPlayer p = players.get(i);
            if (i > 0) sb.append(';');
            sb.append(p.playerId).append(',')
                .append(format(p.x)).append(',')
                .append(format(p.y)).append(',')
                .append(format(p.angle)).append(',')
                .append(format(p.shield)).append(',')
                .append(p.lives).append(',')
                .append(bit(p.shieldActive)).append(',')
                .append(bit(p.alive)).append(',')
                .append(format(p.vx)).append(',')
                .append(format(p.vy)).append(',')
                .append(p.hyperspaceAttempts).append(',')
                .append(sanitizeName(p.playerName));
        }

        sb.append('|');
        for (int i = 0; i < bullets.size(); i++) {
            SnapshotBullet b = bullets.get(i);
            if (i > 0) sb.append(';');
            sb.append(b.ownerPlayerId).append(',')
                .append(format(b.x)).append(',')
                .append(format(b.y));
        }

        sb.append('|');
        for (int i = 0; i < asteroids.size(); i++) {
            SnapshotAsteroid a = asteroids.get(i);
            if (i > 0) sb.append(';');
            sb.append(format(a.x)).append(',')
                .append(format(a.y)).append(',')
                .append(format(a.radius)).append(',')
                .append(format(a.rotation));
            if (a.vertices != null && a.vertices.length == 16) {
                for (int v = 0; v < a.vertices.length; v++) {
                    sb.append(',').append(format(a.vertices[v]));
                }
            }
        }

        sb.append('|');
        for (int i = 0; i < explosions.size(); i++) {
            SnapshotExplosion e = explosions.get(i);
            if (i > 0) sb.append(';');
            sb.append(e.shipExplosion ? 'S' : 'A').append(',')
                .append(format(e.x)).append(',')
                .append(format(e.y)).append(',')
                .append(format(e.r)).append(',')
                .append(format(e.g)).append(',')
                .append(format(e.b)).append(',')
                .append(format(e.a)).append(',')
                .append(format(e.angle)).append(',')
                .append(format(e.vx)).append(',')
                .append(format(e.vy)).append(',')
                .append(format(e.radius)).append(',')
                .append(bit(e.slowFragments));
        }
        return sb.toString();
    }

    public static String welcomeMessage(int playerId) {
        return WELCOME + "|" + playerId;
    }

    public static String errorMessage(String message) {
        return ERROR + "|" + message;
    }

    private static String format(float value) {
        return String.format(Locale.US, "%.3f", value);
    }

    private static String bit(boolean value) {
        return value ? "1" : "0";
    }

    private static boolean parseBit(String value) {
        return "1".equals(value);
    }

    private static String sanitizeName(String value) {
        if (value == null) return "Player";
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return "Player";
        return trimmed.replace('|', '_').replace(';', '_').replace(',', '_');
    }

    public static final class ParsedInput {
        public final boolean rotateLeft;
        public final boolean rotateRight;
        public final boolean thrust;
        public final boolean fire;
        public final boolean shield;
        public final boolean hyperspace;

        public ParsedInput(boolean rotateLeft, boolean rotateRight, boolean thrust,
                   boolean fire, boolean shield, boolean hyperspace) {
            this.rotateLeft = rotateLeft;
            this.rotateRight = rotateRight;
            this.thrust = thrust;
            this.fire = fire;
            this.shield = shield;
            this.hyperspace = hyperspace;
        }
    }

    public static final class SnapshotPlayer {
        public final int playerId;
        public final float x;
        public final float y;
        public final float angle;
        public final float shield;
        public final int lives;
        public final boolean shieldActive;
        public final boolean alive;
        public final float vx;
        public final float vy;
        public final int hyperspaceAttempts;
        public final String playerName;

        public SnapshotPlayer(int playerId, float x, float y, float angle,
                              float shield, int lives, boolean shieldActive,
                      boolean alive, float vx, float vy,
                  int hyperspaceAttempts, String playerName) {
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
            this.playerName = sanitizeName(playerName);
        }
    }

    public static final class SnapshotBullet {
        public final int ownerPlayerId;
        public final float x;
        public final float y;

        public SnapshotBullet(int ownerPlayerId, float x, float y) {
            this.ownerPlayerId = ownerPlayerId;
            this.x = x;
            this.y = y;
        }
    }

    public static final class SnapshotAsteroid {
        public final float x;
        public final float y;
        public final float radius;
        public final float rotation;
        public final float[] vertices;

        public SnapshotAsteroid(float x, float y, float radius, float rotation) {
            this(x, y, radius, rotation, null);
        }

        public SnapshotAsteroid(float x, float y, float radius, float rotation,
                                float[] vertices) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.rotation = rotation;
            this.vertices = vertices == null ? null : vertices.clone();
        }
    }

    public static final class SnapshotExplosion {
        public final boolean shipExplosion;
        public final float x;
        public final float y;
        public final float r;
        public final float g;
        public final float b;
        public final float a;
        public final float angle;
        public final float vx;
        public final float vy;
        public final float radius;
        public final boolean slowFragments;

        public SnapshotExplosion(boolean shipExplosion, float x, float y,
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
    }
}
