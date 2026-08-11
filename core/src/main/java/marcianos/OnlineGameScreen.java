package marcianos;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import marcianos.net.LocalTcpGameServer;
import marcianos.net.NetworkClient;
import marcianos.net.RemoteSnapshot;

/** Online screen using a mock network client until the real server transport is ready. */
public final class OnlineGameScreen extends ScreenAdapter {
    private static final float INFO_WIDTH = 210f;
    private static final float INFO_HEIGHT = 280f;

    private final MarcianosGame game;
    private final OnlineSessionConfig config;
    private final GlyphLayout layout = new GlyphLayout();
    private final Array<Vector2> stars = new Array<>();
    private final Array<ExplosionEffect> explosions = new Array<>();

    private ShapeRenderer renderer;
    private OrthographicCamera camera;
    private OrthographicCamera hudCamera;
    private Viewport viewport;
    private SpriteBatch batch;
    private BitmapFont titleFont;
    private BitmapFont bodyFont;
    private NetworkClient networkClient;
    private RemoteSnapshot snapshot;
    private long lastProcessedExplosionTick = -1L;

    public OnlineGameScreen(MarcianosGame game, OnlineSessionConfig config) {
        this.game = game;
        this.config = config;
    }

    @Override public void show() {
        renderer = new ShapeRenderer();
        camera = new OrthographicCamera();
        hudCamera = new OrthographicCamera();
        viewport = new FitViewport(GameScreen.WORLD_WIDTH, GameScreen.WORLD_HEIGHT, camera);
        updateGameViewport(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        hudCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        hudCamera.update();
        batch = new SpriteBatch();
        titleFont = new BitmapFont();
        titleFont.getData().setScale(1.45f);
        bodyFont = new BitmapFont();
        bodyFont.getData().setScale(1.0f);
        for (int i = 0; i < 120; i++) {
            stars.add(new Vector2(MathUtils.random(GameScreen.WORLD_WIDTH), MathUtils.random(GameScreen.WORLD_HEIGHT)));
        }
        if (config.hostMode()) {
            try {
                LocalTcpGameServer.startIfNeeded(config.port());
            } catch (RuntimeException ex) {
                // Handled in HUD through client connection state.
            } catch (Exception ex) {
                // Handled in HUD through client connection state.
            }
        }
        networkClient = new NetworkClient(config);
        networkClient.connect();
    }

    @Override public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            networkClient.disconnect();
            game.returnToPresentation();
            return;
        }

        InputCommand localInput = readLocalInput();
        networkClient.sendInput(localInput);
        networkClient.update(Math.min(delta, 1f / 30f));
        RemoteSnapshot latest = networkClient.latestSnapshot();
        if (latest != null) {
            snapshot = latest;
            ingestSnapshotExplosionsIfNeeded(latest);
        }
        for (ExplosionEffect explosion : explosions) explosion.update(Math.min(delta, 1f / 30f));
        for (int i = explosions.size - 1; i >= 0; i--) {
            if (explosions.get(i).isDone()) explosions.removeIndex(i);
        }

        Gdx.gl.glClearColor(.025f, .035f, .07f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        drawWorld();
        drawHud();
    }

    @Override public void resize(int width, int height) {
        if (viewport != null) updateGameViewport(width, height);
        if (hudCamera != null) {
            hudCamera.setToOrtho(false, width, height);
            hudCamera.update();
        }
    }

    private InputCommand readLocalInput() {
        return new InputCommand(
            Gdx.input.isKeyPressed(Input.Keys.LEFT),
            Gdx.input.isKeyPressed(Input.Keys.RIGHT),
            Gdx.input.isKeyPressed(Input.Keys.UP),
            Gdx.input.isKeyPressed(Input.Keys.ENTER),
            Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT),
            Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)
        );
    }

    private void drawWorld() {
        viewport.apply(false);
        camera.update();
        renderer.setProjectionMatrix(camera.combined);

        renderer.begin(ShapeRenderer.ShapeType.Filled);
        renderer.setColor(new Color(.42f, .44f, .5f, 1f));
        for (Vector2 star : stars) renderer.circle(star.x, star.y, 1.1f, 4);
        renderer.end();

        if (snapshot == null) return;

        renderer.begin(ShapeRenderer.ShapeType.Line);
        drawAsteroids();
        for (RemoteSnapshot.PlayerState player : snapshot.players()) drawShip(player);
        drawBullets();
        for (ExplosionEffect explosion : explosions) explosion.draw(renderer);
        renderer.end();
    }

    private void ingestSnapshotExplosionsIfNeeded(RemoteSnapshot latest) {
        if (latest.tick() == lastProcessedExplosionTick) return;
        lastProcessedExplosionTick = latest.tick();
        for (RemoteSnapshot.ExplosionState event : latest.explosions()) {
            Color color = new Color(event.r(), event.g(), event.b(), event.a());
            if (event.shipExplosion()) {
                float multiplier = event.slowFragments() ? 0.5f : 1f;
                explosions.add(new ExplosionEffect(
                    event.x(), event.y(), color, event.angle(), event.vx(), event.vy(), multiplier));
            } else {
                explosions.add(new ExplosionEffect(
                    event.x(), event.y(), color, event.angle(), event.vx(), event.vy(),
                    2f, 8, event.radius()));
            }
        }
    }

    private void drawAsteroids() {
        renderer.setColor(Color.GRAY);
        for (RemoteSnapshot.AsteroidState asteroid : snapshot.asteroids()) {
            float[] localVertices = asteroid.vertices();
            if (localVertices == null || localVertices.length != 16) {
                float radius = asteroid.radius();
                int points = 8;
                float[] fallbackVertices = new float[points * 2];
                for (int i = 0; i < points; i++) {
                    float angle = asteroid.rotation() + i * (360f / points);
                    float x = asteroid.x() + MathUtils.cosDeg(angle) * radius;
                    float y = asteroid.y() + MathUtils.sinDeg(angle) * radius;
                    fallbackVertices[i * 2] = x;
                    fallbackVertices[i * 2 + 1] = y;
                }
                for (int i = 0; i < points; i++) {
                    int next = (i + 1) % points;
                    renderer.line(
                        fallbackVertices[i * 2], fallbackVertices[i * 2 + 1],
                        fallbackVertices[next * 2], fallbackVertices[next * 2 + 1]);
                }
                continue;
            }
            for (int i = 0; i < localVertices.length; i += 2) {
                int next = (i + 2) % localVertices.length;
                float x1 = rotatedX(localVertices[i], localVertices[i + 1], asteroid.rotation());
                float y1 = rotatedY(localVertices[i], localVertices[i + 1], asteroid.rotation());
                float x2 = rotatedX(localVertices[next], localVertices[next + 1], asteroid.rotation());
                float y2 = rotatedY(localVertices[next], localVertices[next + 1], asteroid.rotation());
                renderer.line(asteroid.x() + x1, asteroid.y() + y1, asteroid.x() + x2, asteroid.y() + y2);
            }
        }
    }

    private float rotatedX(float x, float y, float rotation) {
        return x * MathUtils.cosDeg(rotation) - y * MathUtils.sinDeg(rotation);
    }

    private float rotatedY(float x, float y, float rotation) {
        return x * MathUtils.sinDeg(rotation) + y * MathUtils.cosDeg(rotation);
    }

    private void drawShip(RemoteSnapshot.PlayerState player) {
        if (!player.alive()) return;
        Color color = desktopColorFor(player.playerId());
        renderer.setColor(color);

        float[] points = new float[10];
        int[] xs = {0, -7, 0, 7, 0};
        int[] ys = {13, -7, -5, -7, 13};
        for (int i = 0; i < xs.length; i++) {
            float px = xs[i] * 2.0f;
            float py = ys[i] * 2.0f;
            points[i * 2] = player.x() + px * MathUtils.cosDeg(player.angle()) - py * MathUtils.sinDeg(player.angle());
            points[i * 2 + 1] = player.y() + px * MathUtils.sinDeg(player.angle()) + py * MathUtils.cosDeg(player.angle());
        }
        for (int i = 0; i < 4; i++) {
            renderer.line(points[i * 2], points[i * 2 + 1], points[i * 2 + 2], points[i * 2 + 3]);
        }
        if (player.shieldActive() && player.shield() > 0f) {
            renderer.setColor(Color.GREEN);
            renderer.circle(player.x(), player.y(), 24f, 24);
            renderer.setColor(color);
        }
    }

    private void drawBullets() {
        for (RemoteSnapshot.BulletState bullet : snapshot.bullets()) {
            renderer.setColor(desktopColorFor(bullet.ownerPlayerId()));
            renderer.line(bullet.x() - 1f, bullet.y() + 1f, bullet.x() + 1f, bullet.y() + 1f);
            renderer.line(bullet.x() + 1f, bullet.y() + 1f, bullet.x() + 1f, bullet.y() - 1f);
            renderer.line(bullet.x() + 1f, bullet.y() - 1f, bullet.x() - 1f, bullet.y() - 1f);
            renderer.line(bullet.x() - 1f, bullet.y() - 1f, bullet.x() - 1f, bullet.y() + 1f);
        }
    }

    private void drawHud() {
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        String role = config.hostMode() ? "HOST" : "CLIENT";
        int players = snapshot == null ? 0 : snapshot.players().size();
        long tick = snapshot == null ? 0 : snapshot.tick();
        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();
        float panelX = screenWidth - INFO_WIDTH;
        float topY = Math.max(20f, screenHeight - INFO_HEIGHT - 20f);
        RemoteSnapshot.PlayerState localPlayer = findLocalPlayer();

        renderer.setProjectionMatrix(hudCamera.combined);
        renderer.begin(ShapeRenderer.ShapeType.Filled);
        renderer.setColor(new Color(.08f, .09f, .13f, 1f));
        renderer.rect(panelX, 0f, INFO_WIDTH, screenHeight);
        renderer.setColor(new Color(.16f, .18f, .25f, 1f));
        if (localPlayer != null) {
            Color localColor = desktopColorFor(localPlayer.playerId());
            renderer.setColor(new Color(localColor.r, localColor.g, localColor.b, 1f));
            renderer.rect(panelX + 8f, topY + 8f, INFO_WIDTH - 16f, INFO_HEIGHT - 16f);
            renderer.setColor(new Color(.08f, .09f, .13f, 1f));
            renderer.rect(panelX + 12f, topY + 35f, INFO_WIDTH - 24f, INFO_HEIGHT - 55f);
            renderer.rect(panelX + 82f, topY + 186f, 105f, 7f);
            renderer.setColor(Color.GREEN);
            renderer.rect(panelX + 82f, topY + 186f,
                105f * MathUtils.clamp(localPlayer.shield() / 100f, 0f, 1f), 7f);
            renderer.setColor(new Color(.16f, .18f, .25f, 1f));
        }
        renderer.rect(panelX, topY - 14f, INFO_WIDTH, 2f);
        renderer.end();

        if (localPlayer != null) {
            renderer.begin(ShapeRenderer.ShapeType.Line);
            drawShipIcons(panelX + 95f, topY + 230f, localPlayer);
            drawJumpIcons(panelX + 95f, topY + 125f, localPlayer);
            renderer.end();
        }

        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();
        if (localPlayer == null) {
            drawLeft(bodyFont, "Esperando snapshot del jugador local...", Color.LIGHT_GRAY,
                panelX + 18f, topY + 160f);
        } else {
            drawLeft(bodyFont, "PLAYER " + localPlayer.playerId(), Color.WHITE, panelX + 18f, topY + 300f);
            drawLeft(bodyFont, "LIVES", Color.LIGHT_GRAY, panelX + 18f, topY + 245f);
            drawLeft(bodyFont, "SHIELD", Color.LIGHT_GRAY, panelX + 18f, topY + 195f);
            drawLeft(bodyFont, "JUMPS", Color.LIGHT_GRAY, panelX + 18f, topY + 140f);
        }

        float summaryY = topY - 34f;
        drawLeft(bodyFont, "OTROS JUGADORES", Color.LIGHT_GRAY, panelX + 18f, summaryY);
        List<RemoteSnapshot.PlayerState> orderedPlayers = orderedPlayers();
        int row = 0;
        for (RemoteSnapshot.PlayerState player : orderedPlayers) {
            if (player.localPlayer()) continue;
            if (row >= 9) {
                drawLeft(bodyFont, "...", Color.LIGHT_GRAY, panelX + 18f, summaryY - 22f - row * 18f);
                break;
            }
            String text = "P" + player.playerId() + ": " + player.lives() + " vidas";
            Color lineColor = player.lives() > 0 ? Color.WHITE : Color.SALMON;
            drawLeft(bodyFont, text, lineColor, panelX + 18f, summaryY - 22f - row * 18f);
            row++;
        }
        if (row == 0) drawLeft(bodyFont, "Sin otros jugadores conectados", Color.LIGHT_GRAY,
            panelX + 18f, summaryY - 22f);

        String networkSummary = "NET " + role + "  " + networkClient.state()
            + "  T" + tick + "  P" + players + "/16";
        drawLeft(bodyFont, networkSummary, Color.DARK_GRAY, panelX + 18f, 86f);
        drawLeft(bodyFont, "Jugador: " + config.playerName(), Color.DARK_GRAY, panelX + 18f, 66f);

        if (!networkClient.lastError().isEmpty()) {
            drawLeft(bodyFont, "Error: " + networkClient.lastError(), Color.SALMON, panelX + 18f, 24f);
            drawLeft(bodyFont, "ESC volver", Color.SALMON, panelX + 18f, 42f);
        } else {
            drawLeft(bodyFont, "ESC volver", Color.SALMON, panelX + 18f, 42f);
        }
        batch.end();
    }

    private RemoteSnapshot.PlayerState findLocalPlayer() {
        if (snapshot == null) return null;
        for (RemoteSnapshot.PlayerState player : snapshot.players()) {
            if (player.localPlayer()) return player;
        }
        return null;
    }

    private List<RemoteSnapshot.PlayerState> orderedPlayers() {
        if (snapshot == null) return new ArrayList<RemoteSnapshot.PlayerState>();
        ArrayList<RemoteSnapshot.PlayerState> ordered = new ArrayList<>(snapshot.players());
        ordered.sort(Comparator.comparingInt(RemoteSnapshot.PlayerState::playerId));
        return ordered;
    }

    private void drawShipIcons(float x, float y, RemoteSnapshot.PlayerState player) {
        renderer.setColor(desktopColorFor(player.playerId()));
        for (int i = 0; i < player.lives(); i++) {
            float cx = x + i * 28f;
            renderer.line(cx, y + 10f, cx - 6f, y - 7f);
            renderer.line(cx - 6f, y - 7f, cx, y - 4f);
            renderer.line(cx, y - 4f, cx + 6f, y - 7f);
            renderer.line(cx + 6f, y - 7f, cx, y + 10f);
        }
    }

    private void drawJumpIcons(float x, float y, RemoteSnapshot.PlayerState player) {
        renderer.setColor(desktopColorFor(player.playerId()));
        for (int i = 0; i < player.hyperspaceAttempts(); i++) {
            float cx = x + i * 22f;
            renderer.line(cx + 4f, y + 10f, cx - 3f, y + 1f);
            renderer.line(cx - 3f, y + 1f, cx + 1f, y + 1f);
            renderer.line(cx + 1f, y + 1f, cx - 4f, y - 10f);
            renderer.line(cx - 4f, y - 10f, cx + 6f, y - 1f);
            renderer.line(cx + 6f, y - 1f, cx + 2f, y - 1f);
            renderer.line(cx + 2f, y - 1f, cx + 4f, y + 10f);
        }
    }

    private static Color desktopColorFor(int playerId) {
        if (playerId == 1) return Color.WHITE;
        if (playerId == 2) return Color.YELLOW;
        return colorFor(playerId, false);
    }

    private void drawLeft(BitmapFont font, String text, Color color, float x, float y) {
        font.setColor(color);
        layout.setText(font, text);
        font.draw(batch, text, x, y);
    }

    private static Color colorFor(int playerId, boolean local) {
        if (local) return Color.WHITE;
        float hueBand = (playerId % 6) / 6f;
        float r = 0.35f + 0.55f * MathUtils.sin(hueBand * MathUtils.PI2 + 0.0f) * 0.5f + 0.28f;
        float g = 0.35f + 0.55f * MathUtils.sin(hueBand * MathUtils.PI2 + 2.1f) * 0.5f + 0.28f;
        float b = 0.35f + 0.55f * MathUtils.sin(hueBand * MathUtils.PI2 + 4.2f) * 0.5f + 0.28f;
        return new Color(MathUtils.clamp(r, 0f, 1f), MathUtils.clamp(g, 0f, 1f), MathUtils.clamp(b, 0f, 1f), 1f);
    }

    private void updateGameViewport(int windowWidth, int windowHeight) {
        int gameWidth = Math.max(1, windowWidth - (int) INFO_WIDTH);
        int gameHeight = Math.max(1, windowHeight);
        viewport.update(gameWidth, gameHeight, true);
        viewport.setScreenBounds(0, 0, gameWidth, gameHeight);
    }

    @Override public void dispose() {
        if (networkClient != null) networkClient.disconnect();
        if (config.hostMode()) LocalTcpGameServer.stopIfRunning(config.port());
        if (renderer != null) renderer.dispose();
        if (batch != null) batch.dispose();
        if (titleFont != null) titleFont.dispose();
        if (bodyFont != null) bodyFont.dispose();
    }
}
