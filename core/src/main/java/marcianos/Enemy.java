package marcianos;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

public final class Enemy {
    private static final float SCALE = 2.2f;
    private static final int MAX_ACTIVE_BULLETS = 1;
    private static final int[] ORIGINAL_X = {0, -7, -7, -4, 4, 7, -7, -4, 4, 7, 7, 0};
    private static final int[] ORIGINAL_Y = {13, 7, 2, -2, -2, -7, -7, -2, -2, 2, 7, 13};

    private final Vector2 position;
    private final Vector2 velocity;
    private final float radius;
    private final Color color = new Color(0.2f, 0.95f, 0.35f, 1f);
    private final Array<Bullet> bullets = new Array<>();
    private float angle;
    private float headingDegrees;
    private float turnRate;
    private float turnRateChangeTimer;
    private float fireCooldown;

    public Enemy() {
        position = new Vector2();
        velocity = new Vector2();
        radius = 34f;
        respawnAtRandomPosition();
    }

    public void update(float delta) {
        turnRateChangeTimer -= delta;
        if (turnRateChangeTimer <= 0f) {
            turnRate = MathUtils.random(-35f, 35f);
            turnRateChangeTimer = MathUtils.random(0.9f, 1.8f);
        }
        headingDegrees += turnRate * delta;
        if (headingDegrees < 0f) headingDegrees += 360f;
        if (headingDegrees >= 360f) headingDegrees -= 360f;
        velocity.setAngleDeg(headingDegrees);
        angle = headingDegrees - 90f;
        position.mulAdd(velocity, delta);
        if (position.y < -radius || position.y > GameScreen.WORLD_HEIGHT + radius
                || position.x < -radius || position.x > GameScreen.WORLD_WIDTH + radius) {
            respawnAtRandomPosition();
        }

        fireCooldown -= delta;
        if (fireCooldown <= 0f && bullets.size < MAX_ACTIVE_BULLETS) {
            bullets.add(new Bullet(position.x, position.y, headingDegrees, color));
            fireCooldown = MathUtils.random(0.6f, 1.2f);
        }
        for (Bullet bullet : bullets) bullet.update(delta);
        for (int i = bullets.size - 1; i >= 0; i--) {
            if (bullets.get(i).life <= 0f) bullets.removeIndex(i);
        }
    }

    public void draw(ShapeRenderer renderer) {
        renderer.setColor(color);
        float[] points = new float[ORIGINAL_X.length * 2];
        float cos = MathUtils.cosDeg(angle);
        float sin = MathUtils.sinDeg(angle);
        for (int i = 0; i < ORIGINAL_X.length; i++) {
            float x = ORIGINAL_X[i] * SCALE;
            float y = ORIGINAL_Y[i] * SCALE;
            points[i * 2] = position.x + x * cos - y * sin;
            points[i * 2 + 1] = position.y + x * sin + y * cos;
        }
        for (int i = 0; i < ORIGINAL_X.length - 1; i++) {
            renderer.line(points[i * 2], points[i * 2 + 1], points[i * 2 + 2], points[i * 2 + 3]);
        }
        for (Bullet bullet : bullets) bullet.draw(renderer);
    }

    public Vector2 getPosition() { return position; }
    public Vector2 getVelocity() { return velocity; }
    public float getAngle() { return angle; }
    public float getRadius() { return radius; }
    public Array<Bullet> getBullets() { return bullets; }
    public void destroyBullet(Bullet bullet) { bullets.removeValue(bullet, true); }

    public int[][] getExplosionSegmentsX() {
        return createSegments(ORIGINAL_X);
    }

    public int[][] getExplosionSegmentsY() {
        return createSegments(ORIGINAL_Y);
    }

    private int[][] createSegments(int[] points) {
        int[][] segments = new int[points.length - 1][2];
        for (int i = 0; i < points.length - 1; i++) {
            segments[i][0] = Math.round(points[i] * SCALE);
            segments[i][1] = Math.round(points[i + 1] * SCALE);
        }
        return segments;
    }

    private void respawnAtRandomPosition() {
        position.set(MathUtils.random(GameScreen.WORLD_WIDTH), MathUtils.random(GameScreen.WORLD_HEIGHT));
        headingDegrees = MathUtils.random(0f, 360f);
        velocity.set(1f, 0f).setAngleDeg(headingDegrees).scl(MathUtils.random(35f, 85f));
        angle = headingDegrees - 90f;
        turnRate = MathUtils.random(-35f, 35f);
        turnRateChangeTimer = MathUtils.random(0.9f, 1.8f);
        fireCooldown = MathUtils.random(0.35f, 0.9f);
        bullets.clear();
    }
}
