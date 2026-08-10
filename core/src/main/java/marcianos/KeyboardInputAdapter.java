package marcianos;

import com.badlogic.gdx.Gdx;

/** Maps local keyboard state to the generic input command format. */
public final class KeyboardInputAdapter {
    private KeyboardInputAdapter() {
    }

    public static InputCommand fromPressedKeys(int[] keys) {
        return new InputCommand(
            Gdx.input.isKeyPressed(keys[PlayerManager.ROTATE_LEFT]),
            Gdx.input.isKeyPressed(keys[PlayerManager.ROTATE_RIGHT]),
            Gdx.input.isKeyPressed(keys[PlayerManager.THRUST]),
            Gdx.input.isKeyPressed(keys[PlayerManager.FIRE]),
            Gdx.input.isKeyPressed(keys[PlayerManager.SHIELD]),
            Gdx.input.isKeyPressed(keys[PlayerManager.HYPERSPACE])
        );
    }
}
