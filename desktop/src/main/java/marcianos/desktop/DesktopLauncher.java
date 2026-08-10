package marcianos.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import marcianos.GameScreen;
import marcianos.MarcianosGame;
import org.lwjgl.glfw.GLFW;

public final class DesktopLauncher {
    private DesktopLauncher() { }

    public static void main(String[] args) {
        String version = System.getProperty("app.version", "dev");
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Cutre-Marcianos " + version);
        config.setWindowedMode(GameScreen.WINDOW_WIDTH, GameScreen.WINDOW_HEIGHT);
        config.setForegroundFPS(60);
        config.useVsync(true);
        config.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override public void created(Lwjgl3Window window) {
                GLFW.glfwFocusWindow(window.getWindowHandle());
            }
        });
        new Lwjgl3Application(new MarcianosGame(), config);
    }
}
