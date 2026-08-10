package marcianos.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import marcianos.InputCommand;

/** Line-based text protocol used by the local TCP implementation. */
public final class TcpProtocol {
    public static final String JOIN = "JOIN";
    public static final String INPUT = "INPUT";
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

    public static String inputMessage(InputCommand input) {
        return INPUT + "|"
            + bit(input.rotateLeft()) + "|"
            + bit(input.rotateRight()) + "|"
            + bit(input.thrust()) + "|"
            + bit(input.fire()) + "|"
            + bit(input.shield()) + "|"
            + bit(input.hyperspace());
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
        String[] parts = line.split("\\|", 5);
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
                String[] p = entry.split(",", 6);
                if (p.length < 5) continue;
                try {
                    int id = Integer.parseInt(p[0]);
                    float x = Float.parseFloat(p[1]);
                    float y = Float.parseFloat(p[2]);
                    float angle = Float.parseFloat(p[3]);
                    float shield = Float.parseFloat(p[4]);
                    boolean shieldActive = p.length >= 6 && parseBit(p[5]);
                    players.add(new RemoteSnapshot.PlayerState(
                        id, x, y, angle, shield, shieldActive, id == localPlayerId));
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
                String[] a = entry.split(",", 4);
                if (a.length != 4) continue;
                try {
                    float x = Float.parseFloat(a[0]);
                    float y = Float.parseFloat(a[1]);
                    float radius = Float.parseFloat(a[2]);
                    float rotation = Float.parseFloat(a[3]);
                    asteroids.add(new RemoteSnapshot.AsteroidState(x, y, radius, rotation));
                } catch (RuntimeException ignored) {
                }
            }
        }

        return new RemoteSnapshot(tick, players, bullets, asteroids);
    }

    public static String snapshotMessage(long tick, List<SnapshotPlayer> players,
                                         List<SnapshotBullet> bullets,
                                         List<SnapshotAsteroid> asteroids) {
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
                .append(bit(p.shieldActive));
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
        public final boolean shieldActive;

        public SnapshotPlayer(int playerId, float x, float y, float angle,
                              float shield, boolean shieldActive) {
            this.playerId = playerId;
            this.x = x;
            this.y = y;
            this.angle = angle;
            this.shield = shield;
            this.shieldActive = shieldActive;
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

        public SnapshotAsteroid(float x, float y, float radius, float rotation) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.rotation = rotation;
        }
    }
}
