package marcianos;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/** Temporary presentation screen kept separate so it can be removed without touching the game. */
public final class PresentationScreen extends ScreenAdapter {
    private static final String PRESENTATION_RESOURCE = "presentationmessage.md";

    private final MarcianosGame game;
    private String titleText;
    private String playerOneText;
    private String playerTwoText;
    private String startMessage;
    private String onlineMessage;
    private String exitMessage;
    private SpriteBatch batch;
    private BitmapFont font;
    private BitmapFont titleFont;
    private ShapeRenderer renderer;
    private OrthographicCamera worldCamera;
    private final Array<Vector2> stars = new Array<>();
    private final Array<Asteroid> asteroids = new Array<>();
    private final Array<DecorativeBullet> bullets = new Array<>();
    private final Array<ExplosionEffect> explosions = new Array<>();
    private DecorativeShip shipOne;
    private DecorativeShip shipTwo;
    private final GlyphLayout layout = new GlyphLayout();

    public PresentationScreen(MarcianosGame game) {
        this.game = game;
    }

    @Override public void show() {
        loadPresentationText();
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.getData().setScale(1.35f);
        titleFont = new BitmapFont();
        titleFont.getData().setScale(3.2f);
        renderer = new ShapeRenderer();
        worldCamera = new OrthographicCamera(GameScreen.WORLD_WIDTH, GameScreen.WORLD_HEIGHT);
        worldCamera.position.set(GameScreen.WORLD_WIDTH / 2f, GameScreen.WORLD_HEIGHT / 2f, 0f);
        worldCamera.update();
        for (int i = 0; i < 100; i++) {
            stars.add(new Vector2(MathUtils.random(GameScreen.WORLD_WIDTH),
                MathUtils.random(GameScreen.WORLD_HEIGHT)));
        }
        for (int i = 0; i < 4; i++) asteroids.add(new Asteroid());
        shipOne = new DecorativeShip(Color.WHITE);
        shipTwo = new DecorativeShip(Color.YELLOW);
    }

    private void loadPresentationText() {
        String[] lines = Gdx.files.internal(PRESENTATION_RESOURCE).readString("UTF-8").split("\\R");
        Array<String> contentLines = new Array<>();
        for (String line : lines) {
            String text = line.trim();
            if (!text.isEmpty()) contentLines.add(text);
        }
        if (contentLines.size < 5) {
            throw new IllegalStateException("Invalid presentation resource: " + PRESENTATION_RESOURCE);
        }
        titleText = contentLines.get(0);
        playerOneText = contentLines.get(1);
        playerTwoText = contentLines.get(2);
        startMessage = contentLines.get(3);
        if (contentLines.size >= 6) {
            onlineMessage = contentLines.get(4);
            exitMessage = contentLines.get(5);
        } else {
            onlineMessage = "Press **JOIN (J)** or **CREATE (C)** for online games";
            exitMessage = contentLines.get(4);
        }
    }

    @Override public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
            game.startOnePlayer();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
            game.startTwoPlayers();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.J)) {
            game.showJoinScreen();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.C)) {
            game.showCreateScreen();
            return;
        }
        updateBackground(Math.min(delta, 1f / 30f));
        Gdx.gl.glClearColor(.025f, .035f, .07f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        drawBackground();
        batch.begin();
        drawBoldCentered(titleFont, titleText, Color.GREEN, Gdx.graphics.getHeight() / 2f + 150f);
        drawColoredCentered(playerOneText, Color.WHITE, Gdx.graphics.getHeight() / 2f + 45f);
        drawColoredCentered(playerTwoText, Color.YELLOW, Gdx.graphics.getHeight() / 2f - 15f);
        drawStartMessage(Gdx.graphics.getHeight() / 2f - 120f);
        drawOnlineMessage(Gdx.graphics.getHeight() / 2f - 180f);
        drawExitMessage(Gdx.graphics.getHeight() / 2f - 240f);
        batch.end();
    }

    private void updateBackground(float delta) {
        shipOne.update(delta);
        shipTwo.update(delta);
        for (Asteroid asteroid : asteroids) asteroid.update(delta);
        for (DecorativeBullet bullet : bullets) bullet.update(delta);
        for (ExplosionEffect explosion : explosions) explosion.update(delta);
        for (int i = explosions.size - 1; i >= 0; i--) {
            if (explosions.get(i).isDone()) explosions.removeIndex(i);
        }
        for (int i = bullets.size - 1; i >= 0; i--) {
            DecorativeBullet bullet = bullets.get(i);
            if (bullet.life <= 0f) bullets.removeIndex(i);
        }
        checkDecorativeCollisions();
    }

    private void drawBackground() {
        renderer.setProjectionMatrix(worldCamera.combined);
        renderer.begin(ShapeRenderer.ShapeType.Filled);
        renderer.setColor(new Color(.22f, .25f, .32f, 1f));
        for (Vector2 star : stars) renderer.circle(star.x, star.y, 1.2f, 4);
        renderer.end();
        renderer.begin(ShapeRenderer.ShapeType.Line);
        for (Asteroid asteroid : asteroids) asteroid.draw(renderer);
        shipOne.draw(renderer);
        shipTwo.draw(renderer);
        for (DecorativeBullet bullet : bullets) bullet.draw(renderer);
        for (ExplosionEffect explosion : explosions) explosion.draw(renderer);
        renderer.end();
    }

    private void checkDecorativeCollisions() {
        for (int bulletIndex = bullets.size - 1; bulletIndex >= 0; bulletIndex--) {
            DecorativeBullet bullet = bullets.get(bulletIndex);
            if (checkBulletAgainstShip(bullet, shipOne) || checkBulletAgainstShip(bullet, shipTwo)) {
                bullets.removeIndex(bulletIndex);
                continue;
            }
            for (int asteroidIndex = 0; asteroidIndex < asteroids.size; asteroidIndex++) {
                Asteroid asteroid = asteroids.get(asteroidIndex);
                if (bullet.position.dst(asteroid.getPosition()) <= asteroid.getRadius()) {
                    bullets.removeIndex(bulletIndex);
                    asteroids.removeIndex(asteroidIndex);
                    addAsteroidExplosion(asteroid);
                    for (Asteroid child : asteroid.split()) asteroids.add(child);
                    break;
                }
            }
        }
        checkShipAsteroidCollision(shipOne);
        checkShipAsteroidCollision(shipTwo);
        if (shipOne.alive && shipTwo.alive && shipOne.position.dst(shipTwo.position) < 32f) {
            explodeShip(shipOne);
            explodeShip(shipTwo);
        }
    }

    private boolean checkBulletAgainstShip(DecorativeBullet bullet, DecorativeShip ship) {
        if (bullet.owner == ship || !ship.alive) return false;
        if (bullet.position.dst(ship.position) >= 18f) return false;
        explodeShip(ship);
        return true;
    }

    private void explodeShip(DecorativeShip ship) {
        if (!ship.alive) return;
        ship.hit();
        explosions.add(new ExplosionEffect(ship.position.x, ship.position.y, ship.color,
            ship.angle, ship.velocity.x, ship.velocity.y, .75f));
    }

    private void addAsteroidExplosion(Asteroid asteroid) {
        explosions.add(new ExplosionEffect(asteroid.getPosition().x, asteroid.getPosition().y,
            new Color(.45f, .45f, .45f, .65f), asteroid.getRotation(),
            asteroid.getVelocity().x, asteroid.getVelocity().y, 1.5f, 8, asteroid.getRadius()));
    }

    private void checkShipAsteroidCollision(DecorativeShip ship) {
        if (!ship.alive) return;
        for (Asteroid asteroid : asteroids) {
            if (ship.position.dst(asteroid.getPosition()) < asteroid.getRadius() + 18f) {
                explodeShip(ship);
                return;
            }
        }
    }

    private void drawCentered(BitmapFont selectedFont, String text, Color color, float y) {
        selectedFont.setColor(color);
        layout.setText(selectedFont, text);
        selectedFont.draw(batch, text, (Gdx.graphics.getWidth() - layout.width) / 2f, y);
    }

    private void drawBoldCentered(BitmapFont selectedFont, String text, Color color, float y) {
        selectedFont.setColor(color);
        layout.setText(selectedFont, text);
        float x = (Gdx.graphics.getWidth() - layout.width) / 2f;
        selectedFont.draw(batch, text, x - .7f, y);
        selectedFont.draw(batch, text, x + .7f, y);
    }

    private void drawColoredCentered(String text, Color baseColor, float y) {
        layout.setText(font, text);
        float x = (Gdx.graphics.getWidth() - layout.width) / 2f;
        int cursor = 0;
        String[] greenWords = {"ROTATE", "THROTTLE", "FIRE", "SHIELD", "HYPERSPACE"};
        while (cursor < text.length()) {
            String keyword = findKeywordAt(text, cursor, greenWords);
            if (keyword != null) {
                drawPart(keyword, Color.GREEN, x, y);
                x += widthOf(keyword);
                cursor += keyword.length();
            } else {
                int next = cursor + 1;
                while (next < text.length() && findKeywordAt(text, next, greenWords) == null) next++;
                String part = text.substring(cursor, next);
                drawPart(part, baseColor, x, y);
                x += widthOf(part);
                cursor = next;
            }
        }
    }

    private String findKeywordAt(String text, int index, String[] keywords) {
        for (String keyword : keywords) if (text.startsWith(keyword, index)) return keyword;
        return null;
    }

    private void drawPart(String text, Color color, float x, float y) {
        font.setColor(color);
        font.draw(batch, text, x, y);
    }

    private void drawStartMessage(float y) {
        drawMarkedCentered(startMessage, y);
    }

    private void drawOnlineMessage(float y) {
        drawMarkedCentered(onlineMessage, y);
    }

    private void drawExitMessage(float y) {
        drawMarkedCentered(exitMessage, y);
    }

    private void drawMarkedCentered(String text, float y) {
        String visibleMessage = text.replace("**", "");
        layout.setText(font, visibleMessage);
        float x = (Gdx.graphics.getWidth() - layout.width) / 2f;
        boolean bold = false;
        int cursor = 0;
        while (cursor < text.length()) {
            if (text.startsWith("**", cursor)) {
                bold = !bold;
                cursor += 2;
                continue;
            }
            int next = cursor + 1;
            while (next < text.length() && !text.startsWith("**", next)) next++;
            String part = text.substring(cursor, next);
            Color color = Color.RED;
            if (bold && part.trim().equals("1")) color = Color.WHITE;
            if (bold && part.trim().equals("2")) color = Color.YELLOW;
            if (bold && part.trim().equals("JOIN (J)")) color = Color.GREEN;
            if (bold && part.trim().equals("CREATE (C)")) color = Color.CYAN;
            if (bold && part.trim().equals("ESC")) color = Color.WHITE;
            if (bold) drawBoldPart(part, color, x, y);
            else drawPart(part, color, x, y);
            x += widthOf(part);
            cursor = next;
        }
    }

    private void drawBoldPart(String text, Color color, float x, float y) {
        drawPart(text, color, x - .5f, y);
        drawPart(text, color, x + .5f, y);
    }

    private float widthOf(String text) {
        layout.setText(font, text);
        return layout.width;
    }

    @Override public void dispose() {
        if (batch != null) batch.dispose();
        if (font != null) font.dispose();
        if (titleFont != null) titleFont.dispose();
        if (renderer != null) renderer.dispose();
    }

    private final class DecorativeShip {
        private final Color color;
        private final Vector2 position = new Vector2();
        private final Vector2 velocity = new Vector2();
        private float angle;
        private float fireTimer;
        private boolean alive = true;
        private float respawnTimer;

        private DecorativeShip(Color color) {
            this.color = new Color(color.r, color.g, color.b, .42f);
            position.set(MathUtils.random(100f, GameScreen.WORLD_WIDTH - 100f),
                MathUtils.random(100f, GameScreen.WORLD_HEIGHT - 100f));
            float heading = MathUtils.random(360f);
            velocity.set(MathUtils.cosDeg(heading), MathUtils.sinDeg(heading))
                .scl(MathUtils.random(18f, 32f));
            angle = heading;
            fireTimer = MathUtils.random(1.5f, 4f);
        }

        private void update(float delta) {
            if (!alive) {
                respawnTimer -= delta;
                if (respawnTimer <= 0f) respawn();
                return;
            }
            position.mulAdd(velocity, delta);
            angle += MathUtils.random(-18f, 18f) * delta;
            wrap(position, 25f);
            fireTimer -= delta;
            if (fireTimer <= 0f) {
                float heading = angle + 90f;
                bullets.add(new DecorativeBullet(position.x, position.y, heading, color, this));
                fireTimer = MathUtils.random(1.5f, 4f);
            }
        }

        private void draw(ShapeRenderer target) {
            if (!alive) return;
            target.setColor(color);
            float[] points = new float[10];
            int[] xs = {0, -7, 0, 7, 0};
            int[] ys = {13, -7, -5, -7, 13};
            for (int i = 0; i < xs.length; i++) {
                float px = xs[i] * 2.2f;
                float py = ys[i] * 2.2f;
                points[i * 2] = position.x + px * MathUtils.cosDeg(angle) - py * MathUtils.sinDeg(angle);
                points[i * 2 + 1] = position.y + px * MathUtils.sinDeg(angle) + py * MathUtils.cosDeg(angle);
            }
            for (int i = 0; i < 4; i++) target.line(points[i * 2], points[i * 2 + 1],
                points[i * 2 + 2], points[i * 2 + 3]);
        }

        private void hit() {
            alive = false;
            respawnTimer = 2f;
        }

        private void respawn() {
            position.set(MathUtils.random(100f, GameScreen.WORLD_WIDTH - 100f),
                MathUtils.random(100f, GameScreen.WORLD_HEIGHT - 100f));
            float heading = MathUtils.random(360f);
            velocity.set(MathUtils.cosDeg(heading), MathUtils.sinDeg(heading))
                .scl(MathUtils.random(18f, 32f));
            angle = heading;
            fireTimer = MathUtils.random(1.5f, 4f);
            alive = true;
        }
    }

    private static final class DecorativeBullet {
        private final Vector2 position;
        private final Vector2 velocity;
        private final Color color;
        private final DecorativeShip owner;
        private float life = 3f;

        private DecorativeBullet(float x, float y, float angle, Color color, DecorativeShip owner) {
            position = new Vector2(x, y);
            velocity = new Vector2(MathUtils.cosDeg(angle), MathUtils.sinDeg(angle)).scl(480f);
            this.color = new Color(color.r, color.g, color.b, .5f);
            this.owner = owner;
        }

        private void update(float delta) {
            position.mulAdd(velocity, delta);
            wrap(position, 2f);
            life -= delta;
        }

        private void draw(ShapeRenderer target) {
            target.setColor(color);
            target.circle(position.x, position.y, 2f, 4);
        }
    }

    private static void wrap(Vector2 position, float margin) {
        if (position.x < -margin) position.x = GameScreen.WORLD_WIDTH + margin;
        if (position.x > GameScreen.WORLD_WIDTH + margin) position.x = -margin;
        if (position.y < -margin) position.y = GameScreen.WORLD_HEIGHT + margin;
        if (position.y > GameScreen.WORLD_HEIGHT + margin) position.y = -margin;
    }
}