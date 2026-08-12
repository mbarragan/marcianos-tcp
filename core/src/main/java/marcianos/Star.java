package marcianos;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;

/** A large star marker that appears at the centre of the play area. */
public final class Star {
    private static final float INITIAL_MASS = 10000f;
    private static final float MASS_INCREMENT = 1000f;
    private static final int MAX_MASS_INCREMENTS = 10;
    private static final float GRAVITATIONAL_CONSTANT = 100f;
    private static final float RADIUS = 21f;
    private static final float ASTERISK_RADIUS = 17f;
    private final float x;
    private final float y;
    private final Vector2 gravityDirection = new Vector2();
    private float mass = INITIAL_MASS;
    private int massIncrementsApplied = 0;

    public Star(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void draw(ShapeRenderer renderer) {
        renderer.setColor(Color.YELLOW);
        renderer.circle(x, y, RADIUS, 32);
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI * i / 3d;
            float dx = (float) Math.cos(angle) * ASTERISK_RADIUS;
            float dy = (float) Math.sin(angle) * ASTERISK_RADIUS;
            renderer.line(x - dx, y - dy, x + dx, y + dy);
        }
    }

    /** Applies the star's gravitational force to one player's ship. */
    public void applyGravity(PlayerManager player, float delta) {
        gravityDirection.set(x - player.getPosition().x, y - player.getPosition().y);
        float distanceSquared = gravityDirection.len2();
        if (distanceSquared < 1f) return;

        float distance = (float) Math.sqrt(distanceSquared);
        gravityDirection.scl(1f / distance);
        float force = GRAVITATIONAL_CONSTANT * mass * PlayerManager.MASS / distanceSquared;
        player.applyForce(gravityDirection.scl(force), delta);
    }

    public void increaseMass() {
        if (massIncrementsApplied >= MAX_MASS_INCREMENTS) return;
        mass += MASS_INCREMENT;
        massIncrementsApplied++;
    }
    public float getMass() { return mass; }

    public float getX() { return x; }
    public float getY() { return y; }
    public float getRadius() { return RADIUS; }
}