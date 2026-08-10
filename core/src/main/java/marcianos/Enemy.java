package marcianos;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

public final class Enemy {
    private final Vector2 position;
    private final Vector2 velocity;
    private final float radius;
    private final Color color = new Color(0.95f, 0.35f, 0.35f, 1f);

    public Enemy() {
        position = new Vector2(MathUtils.random(GameScreen.WORLD_WIDTH), GameScreen.WORLD_HEIGHT + 30f);
        velocity = new Vector2(MathUtils.random(-35f, 35f), MathUtils.random(-75f, -25f));
        radius = MathUtils.random(15f, 28f);
    }

    public void update(float delta) {
        position.mulAdd(velocity, delta);
        if (position.y < -radius) position.y = GameScreen.WORLD_HEIGHT + radius;
        if (position.x < -radius) position.x = GameScreen.WORLD_WIDTH + radius;
        if (position.x > GameScreen.WORLD_WIDTH + radius) position.x = -radius;
    }

    public void draw(ShapeRenderer renderer) {
        renderer.setColor(color);
        renderer.circle(position.x, position.y, radius, 12);
        renderer.setColor(Color.BLACK);
        renderer.circle(position.x - radius * .3f, position.y + radius * .2f, 3f, 8);
        renderer.circle(position.x + radius * .3f, position.y + radius * .2f, 3f, 8);
    }

    public Vector2 getPosition() { return position; }
    public float getRadius() { return radius; }
}
