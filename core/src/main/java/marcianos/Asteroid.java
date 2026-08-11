package marcianos;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/** A single asteroid that enters the game after the first ten seconds. */
public final class Asteroid {
    private static final float MIN_RADIUS = 14f;
    private static final float MAX_RADIUS = 44f;
    private static final float SMALL_MAX_RADIUS = 23f;
    private static final float MEDIUM_MIN_RADIUS = 24f;
    private static final float MEDIUM_MAX_RADIUS = 33f;
    private static final float MIN_SPEED = 20f;
    private static final float MAX_SPEED = 45f;
    private final Vector2 position = new Vector2();
    private final Vector2 velocity = new Vector2();
    private final float[] vertices = new float[16];
    private final float radius;
    private float rotation;

    public Asteroid() {
        // The asteroid can start anywhere in the playable screen, with a
        // random heading and a deliberately small random speed.
        radius = MathUtils.random(MIN_RADIUS, MAX_RADIUS);
        position.set(MathUtils.random(0f, GameScreen.WORLD_WIDTH),
            MathUtils.random(0f, GameScreen.WORLD_HEIGHT));
        float heading = MathUtils.random(360f);
        velocity.set(MathUtils.cosDeg(heading), MathUtils.sinDeg(heading))
            .scl(MathUtils.random(MIN_SPEED, MAX_SPEED));
        rotation = MathUtils.random(360f);
        generateVertices();
    }

    public static Asteroid large() { return new Asteroid(MathUtils.random(34f, MAX_RADIUS)); }
    public static Asteroid medium() { return new Asteroid(MathUtils.random(MEDIUM_MIN_RADIUS, MEDIUM_MAX_RADIUS)); }
    public static Asteroid small() { return new Asteroid(MathUtils.random(MIN_RADIUS, SMALL_MAX_RADIUS)); }

    private Asteroid(float radius) {
        this.radius = radius;
        position.set(MathUtils.random(0f, GameScreen.WORLD_WIDTH),
            MathUtils.random(0f, GameScreen.WORLD_HEIGHT));
        float heading = MathUtils.random(360f);
        velocity.set(MathUtils.cosDeg(heading), MathUtils.sinDeg(heading))
            .scl(MathUtils.random(MIN_SPEED, MAX_SPEED));
        rotation = MathUtils.random(360f);
        generateVertices();
    }

    private Asteroid(float radius, Vector2 position, Vector2 velocity, float rotation) {
        this.radius = radius;
        this.position.set(position);
        this.velocity.set(velocity);
        this.rotation = rotation;
        generateVertices();
    }

    private void generateVertices() {
        for (int i = 0; i < vertices.length; i += 2) {
            float angle = i / 2f * 45f;
            float pointRadius = radius * MathUtils.random(.75f, 1.15f);
            vertices[i] = MathUtils.cosDeg(angle) * pointRadius;
            vertices[i + 1] = MathUtils.sinDeg(angle) * pointRadius;
        }
    }

    /** Splits this asteroid while preserving the parent's linear momentum. */
    public Array<Asteroid> split() {
        Array<Asteroid> children = new Array<>(2);
        if (radius <= SMALL_MAX_RADIUS) return children;

        float childMin = radius <= MEDIUM_MAX_RADIUS ? MIN_RADIUS : MEDIUM_MIN_RADIUS;
        float childMax = radius <= MEDIUM_MAX_RADIUS ? SMALL_MAX_RADIUS : MEDIUM_MAX_RADIUS;
        float radiusOne = MathUtils.random(childMin, childMax);
        float radiusTwo = MathUtils.random(childMin, childMax);
        float massOne = radiusOne * radiusOne;
        float massTwo = radiusTwo * radiusTwo;
        float totalMass = massOne + massTwo;

        float separationAngle = MathUtils.random(360f);
        Vector2 separation = new Vector2(MathUtils.cosDeg(separationAngle),
            MathUtils.sinDeg(separationAngle));
        // Both children are pushed along the line that separates them. The
        // opposite, mass-weighted impulses preserve momentum and guarantee
        // that the children always move away from one another.
        float separationSpeed = MathUtils.random(8f, 16f);
        Vector2 velocityOne = new Vector2(velocity).add(new Vector2(separation).scl(separationSpeed * massTwo / totalMass));
        Vector2 velocityTwo = new Vector2(velocity).sub(new Vector2(separation).scl(separationSpeed * massOne / totalMass));
        Vector2 positionOne = new Vector2(position).mulAdd(separation, radiusOne);
        Vector2 positionTwo = new Vector2(position).mulAdd(separation, -radiusTwo);
        children.add(new Asteroid(radiusOne, positionOne, velocityOne, rotation + MathUtils.random(-20f, 20f)));
        children.add(new Asteroid(radiusTwo, positionTwo, velocityTwo, rotation + MathUtils.random(-20f, 20f)));
        return children;
    }

    public void update(float delta) {
        position.mulAdd(velocity, delta);
        rotation += 25f * delta;
        if (position.y < -radius) position.y = GameScreen.WORLD_HEIGHT + radius;
        if (position.y > GameScreen.WORLD_HEIGHT + radius) position.y = -radius;
        if (position.x < -radius) position.x = GameScreen.WORLD_WIDTH + radius;
        if (position.x > GameScreen.WORLD_WIDTH + radius) position.x = -radius;
    }

    public void draw(ShapeRenderer renderer) {
        renderer.setColor(Color.GRAY);
        for (int i = 0; i < vertices.length; i += 2) {
            int next = (i + 2) % vertices.length;
            float x1 = rotatedX(vertices[i], vertices[i + 1]);
            float y1 = rotatedY(vertices[i], vertices[i + 1]);
            float x2 = rotatedX(vertices[next], vertices[next + 1]);
            float y2 = rotatedY(vertices[next], vertices[next + 1]);
            renderer.line(position.x + x1, position.y + y1, position.x + x2, position.y + y2);
        }
    }

    private float rotatedX(float x, float y) { return x * MathUtils.cosDeg(rotation) - y * MathUtils.sinDeg(rotation); }
    private float rotatedY(float x, float y) { return x * MathUtils.sinDeg(rotation) + y * MathUtils.cosDeg(rotation); }

    public Vector2 getPosition() { return position; }
    public float getRadius() { return radius; }
    public Vector2 getVelocity() { return velocity; }
    public float getRotation() { return rotation; }
    public float[] getVertices() { return vertices.clone(); }
}