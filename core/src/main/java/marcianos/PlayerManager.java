package marcianos;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/** A single parametrised player controller; player 1 and player 2 use this class. */
public final class PlayerManager {
    public static final float MASS = 10f;
    public static final int ROTATE_LEFT = 0;
    public static final int ROTATE_RIGHT = 1;
    public static final int THRUST = 2;
    public static final int FIRE = 3;
    public static final int SHIELD = 4;
    public static final int HYPERSPACE = 5;

    private final Color color;
    private final int[] keys;
    private final Vector2 position = new Vector2();
    private final Vector2 velocity = new Vector2();
    private final Array<Bullet> bullets = new Array<>();
    private float angle;
    private float fireCooldown;
    private float shield = 100f;
    private int ships;
    private int hyperspaceAttempts = 3;
    private boolean hyperspaceWasPressed;
    private boolean alive = true;
    private float respawnTimer;
    private boolean inputEnabled = true;
    private boolean respawnEnabled = true;
    private InputCommand currentInput = InputCommand.none();
    private InputCommand externalInput = InputCommand.none();
    private boolean useExternalInput;

    public PlayerManager(Color color, int rotateLeft, int rotateRight, int thrust, int fire, int shield, int hyperspace) {
        this(color, rotateLeft, rotateRight, thrust, fire, shield, hyperspace, 3);
    }

    public PlayerManager(Color color, int rotateLeft, int rotateRight, int thrust, int fire,
                         int shield, int hyperspace, int lives) {
        this.color = new Color(color);
        this.keys = new int[] {rotateLeft, rotateRight, thrust, fire, shield, hyperspace};
        this.ships = Math.max(0, lives - 1);
        respawn();
    }

    public void respawn() {
        position.set(MathUtils.random(80f, GameScreen.WORLD_WIDTH - 80f),
                MathUtils.random(100f, GameScreen.WORLD_HEIGHT - 80f));
        velocity.setZero();
        angle = MathUtils.random(360f);
        shield = 100f;
        hyperspaceAttempts = 3;
        hyperspaceWasPressed = false;
        alive = true;
        respawnTimer = 0f;
    }

    public void update(float delta, GameScreen game) {
        if (!alive) {
            respawnTimer -= delta;
            if (respawnEnabled && respawnTimer <= 0f && ships >= 0) respawn();
            return;
        }
        if (!inputEnabled) {
            currentInput = InputCommand.none();
        } else if (useExternalInput) {
            currentInput = externalInput;
        } else {
            currentInput = KeyboardInputAdapter.fromPressedKeys(keys);
        }
        if (currentInput.rotateLeft()) angle += 140f * delta;
        if (currentInput.rotateRight()) angle -= 140f * delta;
        if (currentInput.thrust()) {
            float heading = angle + 90f;
            velocity.x += MathUtils.cosDeg(heading) * 60f * delta;
            velocity.y += MathUtils.sinDeg(heading) * 60f * delta;
        }
        position.mulAdd(velocity, delta);
        wrap(position, 22f);
        fireCooldown -= delta;
        if (currentInput.fire() && fireCooldown <= 0f && bullets.size < 3) {
            float heading = angle + 90f;
            bullets.add(new Bullet(position.x + MathUtils.cosDeg(heading) * 20f,
                position.y + MathUtils.sinDeg(heading) * 20f, heading, color));
            fireCooldown = .22f;
        }
        if (currentInput.shield() && shield > 0f) shield = Math.max(0f, shield - 25f * delta);
        boolean hyperspacePressed = currentInput.hyperspace();
        if (hyperspacePressed && !hyperspaceWasPressed && hyperspaceAttempts > 0) {
            position.set(MathUtils.random(80f, GameScreen.WORLD_WIDTH - 80f),
                MathUtils.random(100f, GameScreen.WORLD_HEIGHT - 80f));
            velocity.setZero();
            hyperspaceAttempts--;
        }
        hyperspaceWasPressed = hyperspacePressed;
        for (Bullet bullet : bullets) bullet.update(delta);
        for (int i = bullets.size - 1; i >= 0; i--) if (bullets.get(i).life <= 0f) bullets.removeIndex(i);
    }

    public void disableInputAndRespawn() {
        inputEnabled = false;
        respawnEnabled = false;
    }

    public void setExternalInput(InputCommand input) {
        useExternalInput = true;
        externalInput = input == null ? InputCommand.none() : input;
    }

    public void clearExternalInput() {
        useExternalInput = false;
        externalInput = InputCommand.none();
    }

    public void resetForOnlineRespawn(int lives) {
        ships = Math.max(0, lives - 1);
        inputEnabled = true;
        respawnEnabled = true;
        fireCooldown = 0f;
        bullets.clear();
        currentInput = InputCommand.none();
        externalInput = InputCommand.none();
        respawn();
    }

    /** Applies a force for one simulation step: F = m · a. */
    public void applyForce(Vector2 force, float delta) {
        if (!alive) return;
        velocity.mulAdd(force, delta / MASS);
    }

    public void draw(ShapeRenderer renderer) {
        if (!alive) return;
        renderer.setColor(color);
        // These are the original five points: (0,13), (-7,-7), (0,-5), (7,-7), (0,13).
        // ShapeRenderer.ShapeType.Line preserves the old unfilled PolygonSprite look.
        float[] points = new float[10];
        int[] originalX = {0, -7, 0, 7, 0};
        int[] originalY = {13, -7, -5, -7, 13};
        for (int i = 0; i < originalX.length; i++) {
            float x = originalX[i] * 2.2f;
            float y = originalY[i] * 2.2f;
            points[i * 2] = position.x + x * MathUtils.cosDeg(angle) - y * MathUtils.sinDeg(angle);
            points[i * 2 + 1] = position.y + x * MathUtils.sinDeg(angle) + y * MathUtils.cosDeg(angle);
        }
        for (int i = 0; i < 4; i++) {
            renderer.line(points[i * 2], points[i * 2 + 1], points[i * 2 + 2], points[i * 2 + 3]);
        }
        if (currentInput.shield() && shield > 0f) {
            renderer.setColor(Color.GREEN);
            renderer.circle(position.x, position.y, 29f, 32);
        }
        for (Bullet bullet : bullets) bullet.draw(renderer);
    }

    public boolean hit(float x, float y, float radius, ShipExplosionSink explosionSink) {
        return hit(x, y, radius, explosionSink, false);
    }

    public boolean hit(float x, float y, float radius, ShipExplosionSink explosionSink, boolean slowFragments) {
        if (!alive || Vector2.dst(position.x, position.y, x, y) > radius + 16f) return false;
        if (currentInput.shield() && shield > 0f) {
            shield = Math.max(0f, shield - 20f);
            return false;
        }
        alive = false;
        ships--;
        respawnTimer = 2f;
        bullets.clear();
        if (explosionSink != null) {
            explosionSink.addShipExplosion(position.x, position.y, color, angle, velocity, slowFragments);
        }
        return true;
    }

    public Array<Bullet> getBullets() { return bullets; }
    public void destroyBullet(Bullet bullet) { bullets.removeValue(bullet, true); }
    public Vector2 getPosition() { return position; }
    public Color getColor() { return color; }
    public int getShips() { return Math.max(0, ships); }
    /** Remaining lives, including the currently active ship. */
    public int getLives() { return Math.max(0, ships + 1); }
    /** Adds one life without exceeding the maximum of four lives. */
    public void addLife() { if (getLives() < 4) ships++; }
    public boolean isAlive() { return alive; }
    public int getHyperspaceAttempts() { return hyperspaceAttempts; }
    public float getShield() { return shield; }
    public float getAngle() { return angle; }
    public Vector2 getVelocity() { return velocity; }
    public boolean isShieldActive() { return currentInput.shield() && shield > 0f; }

    private static void wrap(Vector2 p, float margin) {
        if (p.x < -margin) p.x = GameScreen.WORLD_WIDTH + margin;
        if (p.x > GameScreen.WORLD_WIDTH + margin) p.x = -margin;
        if (p.y < -margin) p.y = GameScreen.WORLD_HEIGHT + margin;
        if (p.y > GameScreen.WORLD_HEIGHT + margin) p.y = -margin;
    }

    public static int key(String name) { return Input.Keys.valueOf(name); }
}
