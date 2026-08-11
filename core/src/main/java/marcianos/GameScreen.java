package marcianos;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.badlogic.gdx.ScreenAdapter;

/** Core simulation. Rendering and input are kept separate from game state for a future network layer. */
public final class GameScreen extends ScreenAdapter implements ShipExplosionSink {
    public static final String PLAYER_ONE_NAME = "PLAYER 1";
    public static final String PLAYER_TWO_NAME = "PLAYER 2";
    public static final String MESSAGE_TO_START = "Press to start";
    public static final String MESSAGE_TO_START_AGAIN = "Press to start again";
    
    public static final float WORLD_WIDTH = 1710f;
    public static final float WORLD_HEIGHT = 1000f;
    public static final int WINDOW_WIDTH = 1920;
    public static final int WINDOW_HEIGHT = 1080;
    /** Fixed compact width of each information column, in window pixels. */
    private static final float INFO_WIDTH = 210f;
    private static final float INFO_HEIGHT = 280f;
    
    private static final float ASTEROID_APPEARANCE_TIME = 10f;
    private static final float ASTEROID_SPAWN_INTERVAL = 20f;
    private static final float ONE_PLAYER_ASTEROID_APPEARANCE_TIME = 5f;
    private static final float ONE_PLAYER_ASTEROID_SPAWN_INTERVAL = 10f;
    
    private static final float STAR_APPEARANCE_TIME = 30f;
    private static final float STAR_MASS_INTERVAL = 9f;

    private ShapeRenderer renderer;
    private OrthographicCamera camera;
    private Viewport viewport;
    private OrthographicCamera hudCamera;
    private SpriteBatch hudBatch;
    private BitmapFont hudFont;
    private BitmapFont messageFont;
    private final GlyphLayout messageLayout = new GlyphLayout();
    private PlayerManager playerOne;
    private PlayerManager playerTwo;
    private final Array<ExplosionEffect> explosions = new Array<>();
    private final Array<Asteroid> asteroids = new Array<>();
    private final Array<Vector2> stars = new Array<>();
    private Star star;
    private int scoreOne;
    private boolean awardedLifeAt10000;
    private boolean awardedLifeAt100000;
    private float elapsedTime;
    private float nextAsteroidAppearanceTime = ASTEROID_APPEARANCE_TIME;
    private float nextStarMassIncreaseTime = STAR_APPEARANCE_TIME + STAR_MASS_INTERVAL;
    private boolean starAppeared;
    private boolean gameStarted;
    private boolean gameOver;
    private final boolean startImmediately;
    private final GameMode gameMode;
    private String gameOverResult;
    private Color gameOverColor;
    private final MarcianosGame game;

    public GameScreen() {
        this(null, GameMode.TWO_PLAYERS, false);
    }

    public GameScreen(boolean startImmediately) {
        this(null, GameMode.TWO_PLAYERS, startImmediately);
    }

    public GameScreen(GameMode gameMode, boolean startImmediately) {
        this(null, gameMode, startImmediately);
    }

    public GameScreen(MarcianosGame game, GameMode gameMode, boolean startImmediately) {
        this.game = game;
        this.gameMode = gameMode;
        this.startImmediately = startImmediately;
    }

    @Override public void show() {
        renderer = new ShapeRenderer();
        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
        updateGameViewport(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0f);
        camera.update();
        hudCamera = new OrthographicCamera();
        hudBatch = new SpriteBatch();
        hudFont = new BitmapFont();
        hudFont.getData().setScale(1.05f);
        messageFont = new BitmapFont();
        messageFont.getData().setScale(2.2f);
        for (int i = 0; i < 100; i++) {
            stars.add(new Vector2(MathUtils.random(WORLD_WIDTH), MathUtils.random(WORLD_HEIGHT)));
        }
        playerOne = createPlayerOne();
        playerTwo = gameMode == GameMode.TWO_PLAYERS ? createPlayerTwo() : null;
        createInitialAsteroids();
        nextAsteroidAppearanceTime = gameMode == GameMode.ONE_PLAYER
            ? ONE_PLAYER_ASTEROID_APPEARANCE_TIME : ASTEROID_APPEARANCE_TIME;
        gameStarted = startImmediately;
    }

    @Override public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (game != null) game.returnToPresentation();
            return;
        }
        if (!gameStarted && hasStartInput()) gameStarted = true;
        if (gameOver && hasStartInput()) {
            reset();
            gameStarted = true;
        }
        float frameDelta = Math.min(Gdx.graphics.getDeltaTime(), 1f / 30f);
        if (gameStarted && !gameOver) {
            elapsedTime += frameDelta;
            if (elapsedTime >= nextAsteroidAppearanceTime) {
                asteroids.add(new Asteroid());
                nextAsteroidAppearanceTime += gameMode == GameMode.ONE_PLAYER
                    ? ONE_PLAYER_ASTEROID_SPAWN_INTERVAL : ASTEROID_SPAWN_INTERVAL;
            }
            if (!starAppeared && elapsedTime >= STAR_APPEARANCE_TIME) {
                star = new Star(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f);
                starAppeared = true;
            }
            increaseStarMassIfDue();
            updateWorld(frameDelta);
        } else if (gameStarted && gameOver) {
            // The winner remains in the world, but the match timer and spawn
            // schedule stay frozen after the result has been announced.
            updateWorld(frameDelta);
        }
        for (ExplosionEffect explosion : explosions) explosion.update(frameDelta);
        for (int i = explosions.size - 1; i >= 0; i--) if (explosions.get(i).isDone()) explosions.removeIndex(i);
        checkPlayerCollision();

        Gdx.gl.glClearColor(.025f, .035f, .07f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        // Apply the world viewport before drawing the game. Without this call
        // OpenGL uses the complete window and the 1000x650 world is stretched
        // underneath the information column.
        viewport.apply(false);
        camera.update();
        renderer.setProjectionMatrix(camera.combined);
        renderer.begin(ShapeRenderer.ShapeType.Filled);
        renderer.setColor(new Color(.45f, .45f, .45f, 1f));
        for (Vector2 star : stars) renderer.circle(star.x, star.y, 1.2f, 4);
        renderer.end();
        renderer.begin(ShapeRenderer.ShapeType.Line);
        playerOne.draw(renderer);
        if (playerTwo != null) playerTwo.draw(renderer);
        for (Asteroid asteroid : asteroids) asteroid.draw(renderer);
        if (star != null) star.draw(renderer);
        for (ExplosionEffect explosion : explosions) explosion.draw(renderer);
        renderer.end();
        // HUD uses the complete outer window, including the right-hand column.
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        drawHud();
        drawGameMessage();
    }

    private void increaseStarMassIfDue() {
        if (star == null) return;
        while (elapsedTime >= nextStarMassIncreaseTime) {
            star.increaseMass();
            nextStarMassIncreaseTime += STAR_MASS_INTERVAL;
        }
    }

    private void createInitialAsteroids() {
        if (gameMode != GameMode.ONE_PLAYER) return;
        for (int i = 0; i < 10; i++) asteroids.add(Asteroid.large());
        for (int i = 0; i < 5; i++) asteroids.add(Asteroid.medium());
    }

    private void updateWorld(float delta) {
        if (star != null) {
            star.applyGravity(playerOne, delta);
            if (playerTwo != null) star.applyGravity(playerTwo, delta);
        }
        playerOne.update(delta, this);
        if (playerTwo != null) playerTwo.update(delta, this);
        for (Asteroid asteroid : asteroids) asteroid.update(delta);
        if (playerTwo != null) {
            checkBulletCollisions(playerOne, playerTwo);
            checkBulletCollisions(playerTwo, playerOne);
        }
        checkAsteroidCollisions(playerOne);
        checkAsteroidPlayerCollisions(playerOne);
        checkStarPlayerCollisions(playerOne);
        if (playerTwo != null) {
            checkAsteroidCollisions(playerTwo);
            checkAsteroidPlayerCollisions(playerTwo);
            checkStarPlayerCollisions(playerTwo);
        }
        updateGameOver();
    }

    private boolean hasStartInput() {
        for (int key = 0; key <= Input.Keys.MAX_KEYCODE; key++) {
            if (Gdx.input.isKeyJustPressed(key)) return true;
        }
        for (int button = 0; button < 5; button++) {
            if (Gdx.input.isButtonJustPressed(button)) return true;
        }
        return false;
    }

    private void updateGameOver() {
        if (gameOver) return;
        boolean playerOneOut = playerOne.getLives() == 0;
        boolean playerTwoOut = playerTwo != null && playerTwo.getLives() == 0;
        if (!playerOneOut && !playerTwoOut) return;
        gameOver = true;
        playerOne.disableInputAndRespawn();
        if (playerTwo != null) playerTwo.disableInputAndRespawn();
        if (gameMode == GameMode.ONE_PLAYER) {
            gameOverResult = "SCORE " + scoreOne;
            gameOverColor = Color.YELLOW;
            return;
        }
        if (playerOneOut && playerTwoOut) {
            gameOverResult = "DRAW";
            gameOverColor = Color.YELLOW;
        } else if (playerOneOut) {
            gameOverResult = PLAYER_TWO_NAME + " WINS";
            gameOverColor = playerTwo.getColor();
        } else {
            gameOverResult = PLAYER_ONE_NAME + " WINS";
            gameOverColor = playerOne.getColor();
        }
    }

    private void drawGameMessage() {
        if (gameStarted && !gameOver) return;
        float gameWidth = Gdx.graphics.getWidth() - INFO_WIDTH;
        float centerX = gameWidth / 2f;
        float centerY = Gdx.graphics.getHeight() / 2f;
        hudBatch.setProjectionMatrix(hudCamera.combined);
        hudBatch.begin();
        messageFont.setColor(Color.RED);
        messageLayout.setText(messageFont, gameOver ? "GAME OVER" : MESSAGE_TO_START);
        messageFont.draw(hudBatch, gameOver ? "GAME OVER" : MESSAGE_TO_START,
            centerX - messageLayout.width / 2f, centerY + 30f);
        if (gameOver) {
            messageFont.setColor(gameOverColor);
            messageLayout.setText(messageFont, gameOverResult);
            messageFont.draw(hudBatch, gameOverResult,
                centerX - messageLayout.width / 2f, centerY - 30f);
            messageFont.setColor(Color.RED);
            messageLayout.setText(messageFont, MESSAGE_TO_START_AGAIN);
            messageFont.draw(hudBatch, MESSAGE_TO_START_AGAIN,
                centerX - messageLayout.width / 2f, centerY - 90f);
        }
        hudBatch.end();
    }

    private void checkPlayerCollision() {
        if (playerTwo != null && playerOne.isAlive() && playerTwo.isAlive()
                && playerOne.getPosition().dst(playerTwo.getPosition()) < 32f) {
            playerOne.hit(playerTwo.getPosition().x, playerTwo.getPosition().y, 16f, this);
            playerTwo.hit(playerOne.getPosition().x, playerOne.getPosition().y, 16f, this);
        }
    }

    private void checkBulletCollisions(PlayerManager shooter, PlayerManager target) {
        for (int i = shooter.getBullets().size - 1; i >= 0; i--) {
            Bullet bullet = shooter.getBullets().get(i);
            if (target.isAlive() && bullet.getPosition().dst(target.getPosition()) < 18f) {
                // The collision test above is the bullet's hit test. Use the
                // ship's actual position for destruction so the explosion is
                // always anchored to the ship, as in EffManager's original
                // addShipExplosion(ship.locx, ship.locy, ship) call.
                // The hit radius must match the 18-unit test above. Passing
                // 0 here made impacts between 16 and 18 units appear to hit
                // but fail to decrement a life.
                target.hit(target.getPosition().x, target.getPosition().y, 2f, this);
                // A bullet is consumed even when the shield absorbs the hit.
                shooter.destroyBullet(bullet);
            }
        }
    }

    private void checkAsteroidCollisions(PlayerManager shooter) {
        for (int bulletIndex = shooter.getBullets().size - 1; bulletIndex >= 0; bulletIndex--) {
            Bullet bullet = shooter.getBullets().get(bulletIndex);
            for (int asteroidIndex = asteroids.size - 1; asteroidIndex >= 0; asteroidIndex--) {
                Asteroid asteroid = asteroids.get(asteroidIndex);
                if (bullet.getPosition().dst(asteroid.getPosition()) <= asteroid.getRadius() + 3f) {
                    shooter.destroyBullet(bullet);
                    addScore(shooter, asteroid);
                    replaceAsteroid(asteroidIndex, asteroid);
                    break;
                }
            }
        }
    }

    private void addScore(PlayerManager shooter, Asteroid asteroid) {
        if (shooter == playerOne) {
            if (asteroid.getRadius() <= 23f) scoreOne += 100;
            else if (asteroid.getRadius() <= 33f) scoreOne += 50;
            else scoreOne += 20;
            awardExtraLifeIfReached(10000, true);
            awardExtraLifeIfReached(100000, false);
        }
    }

    private void awardExtraLifeIfReached(int threshold, boolean firstBonus) {
        if (scoreOne < threshold) return;
        if (firstBonus ? awardedLifeAt10000 : awardedLifeAt100000) return;
        if (firstBonus) awardedLifeAt10000 = true;
        else awardedLifeAt100000 = true;
        if (playerOne.getLives() < 4) playerOne.addLife();
    }

    private void checkAsteroidPlayerCollisions(PlayerManager player) {
        if (!player.isAlive()) return;
        for (int asteroidIndex = asteroids.size - 1; asteroidIndex >= 0; asteroidIndex--) {
            Asteroid asteroid = asteroids.get(asteroidIndex);
            float collisionDistance = asteroid.getRadius() + 16f;
            if (player.getPosition().dst(asteroid.getPosition()) <= collisionDistance) {
                // The asteroid is consumed on contact, including when the
                // player's shield absorbs the impact.
                player.hit(asteroid.getPosition().x, asteroid.getPosition().y,
                    asteroid.getRadius(), this);
                replaceAsteroid(asteroidIndex, asteroid);
            }
        }
    }

    private void replaceAsteroid(int index, Asteroid asteroid) {
        asteroids.removeIndex(index);
        addAsteroidExplosion(asteroid);
        for (Asteroid child : asteroid.split()) asteroids.add(child);
    }

    private void checkStarPlayerCollisions(PlayerManager player) {
        if (star == null || !player.isAlive()) return;
        float collisionDistance = star.getRadius() + 16f;
        if (player.getPosition().dst(star.getX(), star.getY()) <= collisionDistance) {
            player.hit(star.getX(), star.getY(), star.getRadius(), this, true);
        }
    }

    private void addAsteroidExplosion(Asteroid asteroid) {
        Vector2 position = asteroid.getPosition();
        Vector2 velocity = asteroid.getVelocity();
        explosions.add(new ExplosionEffect(position.x, position.y, Color.GRAY,
            asteroid.getRotation(), velocity.x, velocity.y, 2f, 8, asteroid.getRadius()));
    }

    public void addShipExplosion(float x, float y, Color color, float angle,
                                 com.badlogic.gdx.math.Vector2 velocity, boolean slowFragments) {
        float shardSpeedMultiplier = slowFragments ? 0.5f : 1f;
        explosions.add(new ExplosionEffect(x, y, color, angle + 90f, velocity.x, velocity.y,
            shardSpeedMultiplier));
    }

    private void drawHud() {
        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();
        float panelWidth = INFO_WIDTH;
        float panelX = screenWidth - panelWidth;
        float panelHeight = gameMode == GameMode.ONE_PLAYER ? INFO_HEIGHT * 2f + 20f : INFO_HEIGHT;
        float topY = Math.max(20f, screenHeight - INFO_HEIGHT - 20f);
        float bottomY = Math.max(0f, topY - INFO_HEIGHT - 20f);
        renderer.setProjectionMatrix(hudCamera.combined);
        renderer.begin(ShapeRenderer.ShapeType.Filled);
        renderer.setColor(new Color(.08f, .09f, .13f, 1f));
        renderer.rect(panelX, 0f, panelWidth, screenHeight);
        renderer.setColor(new Color(.16f, .18f, .25f, 1f));
        if (gameMode == GameMode.ONE_PLAYER) {
            renderer.rect(panelX, 0f, panelWidth, screenHeight);
            drawPlayerPanel(panelX, bottomY, panelWidth, playerOne);
        } else {
            renderer.rect(panelX, bottomY + panelHeight + 10f, panelWidth, 2f);
            drawPlayerPanel(panelX, topY, panelWidth, playerOne);
            drawPlayerPanel(panelX, bottomY, panelWidth, playerTwo);
        }
        renderer.end();

        renderer.begin(ShapeRenderer.ShapeType.Line);
        float playerY = gameMode == GameMode.ONE_PLAYER ? bottomY : topY;
        drawShipIcons(panelX + 95f, playerY + 230f, playerOne);
        drawJumpIcons(panelX + 95f, playerY + 125f, playerOne);
        if (playerTwo != null) {
            drawShipIcons(panelX + 95f, bottomY + 230f, playerTwo);
            drawJumpIcons(panelX + 95f, bottomY + 125f, playerTwo);
        }
        renderer.end();

        drawDigitalTime(panelX, 18f);

        hudBatch.setProjectionMatrix(hudCamera.combined);
        hudBatch.begin();
        drawPlayerText(panelX, playerY, playerOne, Color.WHITE, PLAYER_ONE_NAME,
            "FLECHAS  ARRIBA  ENTER", "CTRL DER");
        if (playerTwo != null) {
            drawPlayerText(panelX, bottomY, playerTwo, Color.YELLOW, PLAYER_TWO_NAME,
                "A/D  W  ESPACIO", "CTRL IZQ");
        }
        hudBatch.end();
        if (gameMode == GameMode.ONE_PLAYER) drawDigitalScore(panelX, playerY + 45f);
    }

    private void drawDigitalTime(float panelX, float y) {
        int totalSeconds = (int) elapsedTime;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        String time = String.format("%02d:%02d", minutes, seconds);
        float digitWidth = 18f;
        float digitHeight = 34f;
        float segment = 5f;
        float gap = 5f;
        float colonWidth = 8f;
        float totalWidth = digitWidth * 4f + gap * 3f + colonWidth + gap;
        float x = panelX + (INFO_WIDTH - totalWidth) / 2f;
        renderer.setProjectionMatrix(hudCamera.combined);
        renderer.begin(ShapeRenderer.ShapeType.Filled);
        renderer.setColor(new Color(.25f, 1f, .35f, 1f));
        for (int i = 0; i < time.length(); i++) {
            if (time.charAt(i) == ':') {
                renderer.circle(x + colonWidth / 2f, y + 11f, 2.5f, 8);
                renderer.circle(x + colonWidth / 2f, y + 24f, 2.5f, 8);
                x += colonWidth + gap;
            } else {
                drawDigitalDigit(x, y, digitWidth, digitHeight, segment, time.charAt(i) - '0');
                x += digitWidth + gap;
            }
        }
        renderer.end();
    }

    private void drawDigitalScore(float panelX, float y) {
        String score = String.format("%06d", scoreOne);
        float digitWidth = 13f;
        float digitHeight = 25f;
        float segment = 4f;
        float gap = 3f;
        float totalWidth = score.length() * digitWidth + (score.length() - 1) * gap;
        float x = panelX + (INFO_WIDTH - totalWidth) / 2f;
        renderer.setProjectionMatrix(hudCamera.combined);
        renderer.begin(ShapeRenderer.ShapeType.Filled);
        renderer.setColor(Color.YELLOW);
        for (int i = 0; i < score.length(); i++) {
            drawDigitalDigit(x, y, digitWidth, digitHeight, segment, score.charAt(i) - '0');
            x += digitWidth + gap;
        }
        renderer.end();
        hudBatch.setProjectionMatrix(hudCamera.combined);
        hudBatch.begin();
        hudFont.setColor(Color.YELLOW);
        hudFont.draw(hudBatch, "SCORE", panelX + 18f, y + 43f);
        hudBatch.end();
    }

    private void drawDigitalDigit(float x, float y, float width, float height, float segment, int digit) {
        final int[] masks = {0x3f, 0x06, 0x5b, 0x4f, 0x66, 0x6d, 0x7d, 0x07, 0x7f, 0x6f};
        int mask = masks[digit];
        if ((mask & 1) != 0) renderer.rect(x + segment, y + height - segment, width - 2f * segment, segment);
        if ((mask & 2) != 0) renderer.rect(x + width - segment, y + height / 2f, segment, height / 2f - segment);
        if ((mask & 4) != 0) renderer.rect(x + width - segment, y, segment, height / 2f - segment);
        if ((mask & 8) != 0) renderer.rect(x + segment, y, width - 2f * segment, segment);
        if ((mask & 16) != 0) renderer.rect(x, y, segment, height / 2f - segment);
        if ((mask & 32) != 0) renderer.rect(x, y + height / 2f, segment, height / 2f - segment);
        if ((mask & 64) != 0) renderer.rect(x + segment, y + height / 2f - segment / 2f,
            width - 2f * segment, segment);
    }

    private void drawPlayerPanel(float x, float y, float width, PlayerManager player) {
        Color color = player.getColor();
        renderer.setColor(new Color(color.r, color.g, color.b, 1f));
        renderer.rect(x + 8f, y + 8f, width - 16f, INFO_HEIGHT - 16f);
        renderer.setColor(new Color(.08f, .09f, .13f, 1f));
        renderer.rect(x + 12f, y + 35f, width - 24f, INFO_HEIGHT - 55f);
        // Draw the depleted part first, then the remaining shield on top so
        // 100% is shown as a completely full green bar.
        renderer.setColor(new Color(.08f, .09f, .13f, 1f));
        renderer.rect(x + 82f, y + 186f, 105f, 7f);
        renderer.setColor(Color.GREEN);
        renderer.rect(x + 82f, y + 186f, 105f * player.getShield() / 100f, 7f);
    }

    private void drawPlayerText(float x, float y, PlayerManager player, Color color,
                                String title, String controls, String hyperKey) {
        hudFont.setColor(color);
        // Leave one font line of empty space above each player heading.
        hudFont.draw(hudBatch, title, x + 18f, y + 300f);
        hudFont.setColor(Color.LIGHT_GRAY);
        hudFont.draw(hudBatch, "LIVES", x + 18f, y + 245f);
        hudFont.draw(hudBatch, "SHIELD", x + 18f, y + 195f);
        hudFont.draw(hudBatch, "JUMPS", x + 18f, y + 140f);
    }

    private void drawShipIcons(float x, float y, PlayerManager player) {
        renderer.setColor(player.getColor());
        // The original player starts with two spare ships; the current ship is
        // represented as the third life icon in the information panel.
        for (int i = 0; i < player.getLives(); i++) {
            float cx = x + i * 28f;
            renderer.line(cx, y + 10f, cx - 6f, y - 7f);
            renderer.line(cx - 6f, y - 7f, cx, y - 4f);
            renderer.line(cx, y - 4f, cx + 6f, y - 7f);
            renderer.line(cx + 6f, y - 7f, cx, y + 10f);
        }
    }

    private void drawJumpIcons(float x, float y, PlayerManager player) {
        renderer.setColor(player.getColor());
        for (int i = 0; i < player.getHyperspaceAttempts(); i++) {
            float cx = x + i * 22f;
            renderer.line(cx + 4f, y + 10f, cx - 3f, y + 1f);
            renderer.line(cx - 3f, y + 1f, cx + 1f, y + 1f);
            renderer.line(cx + 1f, y + 1f, cx - 4f, y - 10f);
            renderer.line(cx - 4f, y - 10f, cx + 6f, y - 1f);
            renderer.line(cx + 6f, y - 1f, cx + 2f, y - 1f);
            renderer.line(cx + 2f, y - 1f, cx + 4f, y + 10f);
        }
    }

    private void reset() {
        scoreOne = 0;
        awardedLifeAt10000 = false;
        awardedLifeAt100000 = false;
        elapsedTime = 0f;
        nextAsteroidAppearanceTime = gameMode == GameMode.ONE_PLAYER
            ? ONE_PLAYER_ASTEROID_APPEARANCE_TIME : ASTEROID_APPEARANCE_TIME;
        nextStarMassIncreaseTime = STAR_APPEARANCE_TIME + STAR_MASS_INTERVAL;
        star = null;
        starAppeared = false;
        gameOver = false;
        gameOverResult = null;
        gameOverColor = null;
        explosions.clear();
        asteroids.clear();
        playerOne = createPlayerOne();
        playerTwo = gameMode == GameMode.TWO_PLAYERS ? createPlayerTwo() : null;
        createInitialAsteroids();
    }

    private PlayerManager createPlayerOne() {
        return new PlayerManager(Color.WHITE, Input.Keys.LEFT, Input.Keys.RIGHT, Input.Keys.UP,
            Input.Keys.ENTER, Input.Keys.SHIFT_RIGHT, Input.Keys.CONTROL_RIGHT,
            4);
    }

    private PlayerManager createPlayerTwo() {
        return new PlayerManager(Color.YELLOW, Input.Keys.A, Input.Keys.D, Input.Keys.W,
            Input.Keys.SPACE, Input.Keys.SHIFT_LEFT, Input.Keys.CONTROL_LEFT,
            4);
    }

    @Override public void resize(int width, int height) {
        updateGameViewport(width, height);
        hudCamera.setToOrtho(false, width, height);
        hudCamera.update();
    }

    private void updateGameViewport(int windowWidth, int windowHeight) {
        int gameWidth = Math.max(1, windowWidth - (int) INFO_WIDTH);
        int gameHeight = Math.max(1, windowHeight);
        // The game receives everything left of the information column. FitViewport
        // preserves the original 1000:650 aspect ratio and adds letterboxing when
        // the resized area does not have that ratio.
        viewport.update(gameWidth, gameHeight, true);
        viewport.setScreenBounds(0, 0, gameWidth, gameHeight);
    }
    public float getElapsedTime() { return elapsedTime; }

    @Override public void dispose() { renderer.dispose(); hudBatch.dispose(); hudFont.dispose(); messageFont.dispose(); }
}
