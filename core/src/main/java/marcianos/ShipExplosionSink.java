package marcianos;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;

/** Allows world simulation code to spawn ship explosions without depending on a screen class. */
public interface ShipExplosionSink {
    void addShipExplosion(float x, float y, Color color, float angle,
                          Vector2 velocity, boolean slowFragments);
}