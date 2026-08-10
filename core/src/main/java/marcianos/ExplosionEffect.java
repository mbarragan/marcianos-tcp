package marcianos;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

/** Flying polygon segments used for ship and asteroid explosions. */
public final class ExplosionEffect {
    private static final int[][] EX = {{7, 0}, {-7, 0}, {-7, 0}, {7, 0}};
    private static final int[][] EY = {{13, -7}, {13, -7}, {-7, -5}, {-5, -7}};

    private Shard[] edges;
    private float duration;
    private float updates;
    private boolean done;

    public ExplosionEffect(float x, float y, Color color, float theta, float vx, float vy) {
        this(x, y, color, theta, vx, vy, 3f, EX, EY, 1f);
    }

    public ExplosionEffect(float x, float y, Color color, float theta, float vx, float vy,
                           float shardSpeedMultiplier) {
        this(x, y, color, theta, vx, vy, 3f, EX, EY, shardSpeedMultiplier);
    }

    public ExplosionEffect(float x, float y, Color color, float theta, float vx, float vy,
                           float duration, int segments, float radius) {
        int[][] px = new int[segments][2];
        int[][] py = new int[segments][2];
        for (int i = 0; i < segments; i++) {
            float firstAngle = i * 360f / segments;
            float secondAngle = (i + 1) * 360f / segments;
            px[i][0] = Math.round(MathUtils.cosDeg(firstAngle) * radius);
            py[i][0] = Math.round(MathUtils.sinDeg(firstAngle) * radius);
            px[i][1] = Math.round(MathUtils.cosDeg(secondAngle) * radius);
            py[i][1] = Math.round(MathUtils.sinDeg(secondAngle) * radius);
        }
        initialize(x, y, color, theta, vx, vy, duration, px, py, 1f);
    }

    private ExplosionEffect(float x, float y, Color color, float theta, float vx, float vy,
                            float duration, int[][] px, int[][] py, float shardSpeedMultiplier) {
        initialize(x, y, color, theta, vx, vy, duration, px, py, shardSpeedMultiplier);
    }

    private void initialize(float x, float y, Color color, float theta, float vx, float vy,
                            float duration, int[][] px, int[][] py, float shardSpeedMultiplier) {
        this.duration = duration;
        edges = new Shard[px.length];
        for (int i = 0; i < edges.length; i++) {
            // Original constructor: RotateablePolygon(ex[i], ey[i], 2,
            // locx, locy, color, 1, 2, 0), followed by setAngle(theta - 45).
            int rotationRate = MathUtils.random(0, 5) - 2;
                edges[i] = new Shard(x, y, px[i], py[i], theta - 45f, rotationRate,
                    vx, vy, color, shardSpeedMultiplier);
        }
    }

    public void update(float delta) {
        updates += delta * 60f;
        if (updates < duration * 60f) {
            for (Shard edge : edges) edge.update(delta);
        } else {
            done = true;
        }
    }

    public boolean isDone() { return done; }

    public void draw(ShapeRenderer renderer) {
        for (Shard edge : edges) edge.draw(renderer);
    }

    private static final class Shard {
        private float x;
        private float y;
        private final int[] px;
        private final int[] py;
        private float angle;
        private final float rotationRate;
        private final float vx;
        private final float vy;
        private final Color color;
        private final float radialVx;
        private final float radialVy;

        private Shard(float x, float y, int[] px, int[] py, float angle,
                  float rotationRate, float vx, float vy, Color color,
                  float shardSpeedMultiplier) {
            this.x = x;
            this.y = y;
            this.px = px;
            this.py = py;
            this.angle = angle;
            this.rotationRate = rotationRate;
            this.vx = vx;
            this.vy = vy;
            this.color = new Color(color);
            float middleX = (px[0] + px[1]) * .5f;
            float middleY = (py[0] + py[1]) * .5f;
            float length = (float)Math.sqrt(middleX * middleX + middleY * middleY);
            float localX = middleX / length;
            float localY = middleY / length;
            float cos = MathUtils.cosDeg(angle);
            float sin = MathUtils.sinDeg(angle);
            // The radial component only opens the four segments. It must not
            // dominate the ship's inherited velocity.
            float radialSpeed = MathUtils.random(9f, 21f) * shardSpeedMultiplier;
            radialVx = (localX * cos - localY * sin) * radialSpeed;
            radialVy = (localX * sin + localY * cos) * radialSpeed;
        }

        private void update(float delta) {
            x += (vx + radialVx) * delta;
            y += (vy + radialVy) * delta;
            angle += rotationRate * delta * 60f;
        }

        private void draw(ShapeRenderer renderer) {
            renderer.setColor(color);
            float cos = MathUtils.cosDeg(angle);
            float sin = MathUtils.sinDeg(angle);
            float x1 = x + px[0] * cos - py[0] * sin;
            float y1 = y + px[0] * sin + py[0] * cos;
            float x2 = x + px[1] * cos - py[1] * sin;
            float y2 = y + px[1] * sin + py[1] * cos;
            renderer.line(x1, y1, x2, y2);
        }
    }
}
