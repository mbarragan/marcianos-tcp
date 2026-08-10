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
import marcianos.net.LocalTcpGameServer;
import marcianos.net.NetworkClient;
import marcianos.net.RemoteSnapshot;

/** Online screen using a mock network client until the real server transport is ready. */
public final class OnlineGameScreen extends ScreenAdapter {
    private final MarcianosGame game;
    private final OnlineSessionConfig config;
    private final GlyphLayout layout = new GlyphLayout();
    private final Array<Vector2> stars = new Array<>();

    private ShapeRenderer renderer;
    private OrthographicCamera camera;
    private Viewport viewport;
    private SpriteBatch batch;
    private BitmapFont titleFont;
    private BitmapFont bodyFont;
    private NetworkClient networkClient;
    private RemoteSnapshot snapshot;

    public OnlineGameScreen(MarcianosGame game, OnlineSessionConfig config) {
        this.game = game;
        this.config = config;
    }

    @Override public void show() {
        renderer = new ShapeRenderer();
        camera = new OrthographicCamera();
        viewport = new FitViewport(GameScreen.WORLD_WIDTH, GameScreen.WORLD_HEIGHT, camera);
        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
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
        if (latest != null) snapshot = latest;

        Gdx.gl.glClearColor(.025f, .035f, .07f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        drawWorld();
        drawHud();
    }

    @Override public void resize(int width, int height) {
        if (viewport != null) viewport.update(width, height, true);
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
        renderer.end();
    }

    private void drawAsteroids() {
        renderer.setColor(Color.GRAY);
        for (RemoteSnapshot.AsteroidState asteroid : snapshot.asteroids()) {
            float radius = asteroid.radius();
            int points = 8;
            float[] vertices = new float[points * 2];
            for (int i = 0; i < points; i++) {
                float angle = asteroid.rotation() + i * (360f / points);
                float x = asteroid.x() + MathUtils.cosDeg(angle) * radius;
                float y = asteroid.y() + MathUtils.sinDeg(angle) * radius;
                vertices[i * 2] = x;
                vertices[i * 2 + 1] = y;
            }
            for (int i = 0; i < points; i++) {
                int next = (i + 1) % points;
                renderer.line(vertices[i * 2], vertices[i * 2 + 1], vertices[next * 2], vertices[next * 2 + 1]);
            }
        }
    }

    private void drawShip(RemoteSnapshot.PlayerState player) {
        Color color = colorFor(player.playerId(), player.localPlayer());
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
            renderer.setColor(colorFor(bullet.ownerPlayerId(), false));
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

        batch.begin();
        drawLeft(titleFont, "ONLINE " + role + " MODE", Color.GREEN, 16f, Gdx.graphics.getHeight() - 16f);
        drawLeft(bodyFont, "Player: " + config.playerName(), Color.WHITE, 16f, Gdx.graphics.getHeight() - 44f);
        drawLeft(bodyFont, "Target: " + config.host() + ":" + config.port(), Color.WHITE, 16f, Gdx.graphics.getHeight() - 66f);
        drawLeft(bodyFont, "State: " + networkClient.state(), Color.CYAN, 16f, Gdx.graphics.getHeight() - 88f);
        drawLeft(bodyFont, "Snapshot tick: " + tick + " | Players: " + players + "/16", Color.LIGHT_GRAY, 16f, Gdx.graphics.getHeight() - 110f);
        drawLeft(bodyFont, "Controls sample: arrows/up/enter/shift/ctrl are sent as input commands", Color.LIGHT_GRAY, 16f,
            Gdx.graphics.getHeight() - 132f);
        if (!networkClient.lastError().isEmpty()) {
            drawLeft(bodyFont, "Last error: " + networkClient.lastError(), Color.SALMON, 16f, Gdx.graphics.getHeight() - 154f);
            drawLeft(bodyFont, "ESC return to presentation", Color.SALMON, 16f, Gdx.graphics.getHeight() - 176f);
        } else {
            drawLeft(bodyFont, "ESC return to presentation", Color.SALMON, 16f, Gdx.graphics.getHeight() - 154f);
        }
        batch.end();
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

    @Override public void dispose() {
        if (networkClient != null) networkClient.disconnect();
        if (config.hostMode()) LocalTcpGameServer.stopIfRunning(config.port());
        if (renderer != null) renderer.dispose();
        if (batch != null) batch.dispose();
        if (titleFont != null) titleFont.dispose();
        if (bodyFont != null) bodyFont.dispose();
    }
}
