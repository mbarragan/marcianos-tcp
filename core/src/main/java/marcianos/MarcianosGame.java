package marcianos;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;

/** Application root that owns the removable presentation screen and the game screen. */
public final class MarcianosGame extends Game {
    @Override public void create() {
        setScreen(new PresentationScreen(this));
    }

    public void showJoinScreen() {
        Screen previousScreen = getScreen();
        setScreen(new JoinScreen(this));
        if (previousScreen != null) previousScreen.dispose();
    }

    public void showCreateScreen() {
        Screen previousScreen = getScreen();
        setScreen(new CreateScreen(this));
        if (previousScreen != null) previousScreen.dispose();
    }

    public void startOnlineGame(boolean hostMode) {
        startOnlineGame(new OnlineSessionConfig(hostMode, "localhost", 7777, "Player"));
    }

    public void startOnlineGame(OnlineSessionConfig config) {
        Screen previousScreen = getScreen();
        setScreen(new OnlineGameScreen(this, config));
        if (previousScreen != null) previousScreen.dispose();
    }

    public void startTwoPlayers() {
        Screen previousScreen = getScreen();
        setScreen(new GameScreen(this, GameMode.TWO_PLAYERS, true));
        if (previousScreen != null) previousScreen.dispose();
    }

    public void startOnePlayer() {
        Screen previousScreen = getScreen();
        setScreen(new GameScreen(this, GameMode.ONE_PLAYER, true));
        if (previousScreen != null) previousScreen.dispose();
    }

    public void returnToPresentation() {
        Screen previousScreen = getScreen();
        setScreen(new PresentationScreen(this));
        if (previousScreen != null) previousScreen.dispose();
    }
}