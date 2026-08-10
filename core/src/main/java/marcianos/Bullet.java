package marcianos;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

public final class Bullet {
    private final Vector2 position;
    private final Vector2 velocity;
    private final Color color;
    // The original Fire remains active for its configured travel interval.
    // This port uses three times the previous range requested for the game.
    float life = 4.2f;

    private static final int[] FIRE_X = {-1, 1, 1, -1, -1};
    private static final int[] FIRE_Y = {1, 1, -1, -1, 1};

    public Bullet(float x, float y, float angle, Color color) {
        position = new Vector2(x, y);
        // Original Fire.initialize: velocity = round(8*cos(theta)), round(8*sin(theta))
        velocity = new Vector2(MathUtils.cosDeg(angle), MathUtils.sinDeg(angle)).scl(8f * 60f);
        this.color = new Color(color);
    }

    public void update(float delta) {
        position.mulAdd(velocity, delta);
        wrap(position, 2f);
        life -= delta;
    }
    public void draw(ShapeRenderer renderer) {
        renderer.setColor(color);
        for (int i = 0; i < FIRE_X.length - 1; i++) {
            renderer.line(position.x + FIRE_X[i], position.y + FIRE_Y[i],
                    position.x + FIRE_X[i + 1], position.y + FIRE_Y[i + 1]);
        }
    }
    public Vector2 getPosition() { return position; }
    public void remove() { life = 0f; }

    private static void wrap(Vector2 p, float margin) {
        if (p.x < -margin) p.x = GameScreen.WORLD_WIDTH + margin;
        if (p.x > GameScreen.WORLD_WIDTH + margin) p.x = -margin;
        if (p.y < -margin) p.y = GameScreen.WORLD_HEIGHT + margin;
        if (p.y > GameScreen.WORLD_HEIGHT + margin) p.y = -margin;
    }
}
