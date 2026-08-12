package marcianos;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import javax.swing.SwingUtilities;

/** Join screen with basic host and port configuration. */
public final class JoinScreen extends ScreenAdapter {
    private enum UiState {
        READY,
        CONNECTING,
        ERROR
    }

    private final MarcianosGame game;
    private final GlyphLayout layout = new GlyphLayout();
    private SpriteBatch batch;
    private BitmapFont titleFont;
    private BitmapFont bodyFont;
    private String host = "127.0.0.1";
    private String playerName = "Player";
    private int port = 7777;
    private UiState uiState = UiState.READY;
    private String statusMessage = "Press H to edit host (LAN/public IP), P port, N name.";
    private float connectTimer;

    public JoinScreen(MarcianosGame game) {
        this.game = game;
    }

    @Override public void show() {
        batch = new SpriteBatch();
        titleFont = new BitmapFont();
        titleFont.getData().setScale(2.2f);
        bodyFont = new BitmapFont();
        bodyFont.getData().setScale(1.15f);
    }

    @Override public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.returnToPresentation();
            return;
        }

        if (uiState == UiState.CONNECTING) {
            connectTimer -= delta;
            if (connectTimer <= 0f) {
                game.startOnlineGame(new OnlineSessionConfig(false, host, port, playerName));
                return;
            }
        } else {
            handleEditingInput();
            if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) attemptJoin();
        }

        Gdx.gl.glClearColor(.025f, .035f, .07f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.begin();
        float centerY = Gdx.graphics.getHeight() / 2f;
        drawCentered(titleFont, "JOIN ONLINE GAME", Color.GREEN, centerY + 140f);
        drawCentered(bodyFont, "Host: " + host, Color.WHITE, centerY + 70f);
        drawCentered(bodyFont, "Port: " + port, Color.WHITE, centerY + 35f);
        drawCentered(bodyFont, "Player: " + playerName, Color.WHITE, centerY);
        drawCentered(bodyFont, "ENTER join | H edit host | P edit port | N edit name", Color.CYAN, centerY - 55f);
        drawCentered(bodyFont, "Use host machine LAN/public IP, not localhost", Color.YELLOW, centerY - 75f);
        drawCentered(bodyFont, statusMessage, uiState == UiState.ERROR ? Color.SALMON : Color.LIGHT_GRAY, centerY - 95f);
        drawCentered(bodyFont, "ESC return to presentation", Color.RED, centerY - 135f);
        batch.end();
    }

    private void handleEditingInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.H)) {
            requestTextInput(new Input.TextInputListener() {
                @Override public void input(String text) {
                    String value = text == null ? "" : text.trim();
                    if (!value.isEmpty()) host = value;
                }

                @Override public void canceled() {
                }
            }, "Join Host", host, "e.g. 192.168.1.40 or public IP");
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.P)) {
            requestTextInput(new Input.TextInputListener() {
                @Override public void input(String text) {
                    try {
                        int parsed = Integer.parseInt(text.trim());
                        if (parsed > 0 && parsed <= 65535) port = parsed;
                    } catch (RuntimeException ignored) {
                        uiState = UiState.ERROR;
                        statusMessage = "Invalid port. Use a value between 1 and 65535.";
                    }
                }

                @Override public void canceled() {
                }
            }, "Join Port", Integer.toString(port), "e.g. 7777");
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.N)) {
            requestTextInput(new Input.TextInputListener() {
                @Override public void input(String text) {
                    String value = text == null ? "" : text.trim();
                    if (!value.isEmpty()) playerName = value;
                }

                @Override public void canceled() {
                }
            }, "Player Name", playerName, "visible name");
        }
    }

    private void requestTextInput(Input.TextInputListener listener, String title,
                                  String text, String hint) {
        try {
            final String[] value = new String[1];
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override public void run() {
                    value[0] = SwingTextInputDialog.show(title, text);
                }
            });
            if (value[0] == null) listener.canceled();
            else listener.input(value[0]);
            return;
        } catch (RuntimeException ignored) {
            // Fallback to libGDX dialog when Swing is unavailable.
        } catch (Exception ignored) {
            // Fallback to libGDX dialog when Swing is unavailable.
        }
        Gdx.input.getTextInput(listener, title, text, hint);
    }

    private void attemptJoin() {
        if (host.trim().isEmpty()) {
            uiState = UiState.ERROR;
            statusMessage = "Host is required.";
            return;
        }
        if (port < 1 || port > 65535) {
            uiState = UiState.ERROR;
            statusMessage = "Port must be between 1 and 65535.";
            return;
        }
        uiState = UiState.CONNECTING;
        statusMessage = "Connecting (stub)...";
        connectTimer = 0.25f;
    }

    private void drawCentered(BitmapFont font, String text, Color color, float y) {
        font.setColor(color);
        layout.setText(font, text);
        font.draw(batch, text, (Gdx.graphics.getWidth() - layout.width) / 2f, y);
    }

    @Override public void dispose() {
        if (batch != null) batch.dispose();
        if (titleFont != null) titleFont.dispose();
        if (bodyFont != null) bodyFont.dispose();
    }
}
