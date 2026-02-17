package ru.arssedot.spring.field;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;

import ru.arssedot.spring.model.Arrow;
import ru.arssedot.spring.model.Player;
import ru.arssedot.spring.model.Target;

import java.util.function.BiConsumer;

/**
 * Игровое поле. Содержит игровой цикл, управление состоянием,
 * создание сущностей и проверку столкновений.
 * Отрисовка делегируется {@link GameRenderer}.
 */
public class GameField extends Canvas {

    //константы компоновки
    public static final double NEAR_X = 380;
    public static final double FAR_X = 520;
    private static final double NEAR_RADIUS = 28;
    private static final double FAR_RADIUS = 14;

    //настройка скорости
    private volatile double arrowSpeed = 5.0;
    private volatile double targetSpeed = 2.0;

    //сущности
    private final Player player;
    private volatile Target nearTarget;
    private volatile Target farTarget;
    private volatile Arrow currentArrow;

    //состояние игры
    private volatile int score;
    private volatile int shots;
    private volatile boolean gameRunning;
    private volatile boolean gamePaused;

    private Thread gameLoopThread;

    //фиксация попадание
    private volatile double hitX;
    private volatile double hitY;
    private volatile long hitTime;

    //наблюдатель для обратной связи с формой
    private BiConsumer<Integer, Integer> onStatsChanged;

    //поле рендеринга
    private final double fieldWidth;
    private final double fieldHeight;
    private final GameRenderer renderer;
    private volatile boolean renderPending;

    public GameField(double width, double height) {
        super(width, height);
        this.fieldWidth = width;
        this.fieldHeight = height;
        this.player = new Player(height);
        this.renderer = new GameRenderer(width, height, NEAR_X, FAR_X);
        setFocusTraversable(true);
        render();
    }

    public void setOnStatsChanged(BiConsumer<Integer, Integer> callback) {
        this.onStatsChanged = callback;
    }

    public void setMoveUp(boolean v) {
        player.setMoveUp(v);
    }

    public void setMoveDown(boolean v) {
        player.setMoveDown(v);
    }

    public void setArrowSpeed(double speed) {
        this.arrowSpeed = speed;
    }

    public void setTargetSpeed(double speed) {
        this.targetSpeed = speed;
        if (nearTarget != null) nearTarget.setSpeed(speed);
        if (farTarget != null) farTarget.setSpeed(speed * 2);
    }

    //управление игрой

    public void startGame() {
        if (gameRunning) stopGame();

        score = 0;
        shots = 0;
        player.reset(fieldHeight);
        currentArrow = null;
        hitTime = 0;
        gameRunning = true;
        gamePaused = false;

        notifyStats();

        nearTarget = new Target(NEAR_X, NEAR_RADIUS, targetSpeed, fieldHeight);
        farTarget = new Target(FAR_X, FAR_RADIUS, targetSpeed * 2, fieldHeight);
        nearTarget.start();
        farTarget.start();

        startGameLoop();
    }

    public void stopGame() {
        gameRunning = false;
        if (nearTarget != null) nearTarget.stop();
        if (farTarget != null) farTarget.stop();
        if (currentArrow != null) currentArrow.deactivate();
        if (gameLoopThread != null) gameLoopThread.interrupt();
        Platform.runLater(this::render);
    }

    public void pauseGame() {
        if (!gameRunning) return;
        gamePaused = true;
        if (nearTarget != null) nearTarget.pause();
        if (farTarget != null) farTarget.pause();
        if (currentArrow != null) currentArrow.pause();
    }

    public void resumeGame() {
        if (!gameRunning) return;
        gamePaused = false;
        if (nearTarget != null) nearTarget.resume();
        if (farTarget != null) farTarget.resume();
        if (currentArrow != null) currentArrow.resume();
    }

    public boolean isPaused()  { return gamePaused; }
    public boolean isRunning() { return gameRunning; }

    public void shoot() {
        if (!gameRunning || gamePaused) return;
        if (currentArrow != null && currentArrow.isActive()) return;

        shots++;
        notifyStats();
        currentArrow = new Arrow(Player.PLAYER_X + 20, player.getY(), arrowSpeed, fieldWidth + 50);
        currentArrow.start();
    }

    //игровой цикл

    private void startGameLoop() {
        gameLoopThread = new Thread(() -> {
            while (gameRunning) {
                if (!gamePaused) {
                    player.update();
                    checkCollisions();
                }
                if (!renderPending) {
                    renderPending = true;
                    Platform.runLater(() -> {
                        render();
                        renderPending = false;
                    });
                }
                try {
                    Thread.sleep(16);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "GameLoop");
        gameLoopThread.setDaemon(true);
        gameLoopThread.start();
    }

    //столкновения 

    private void checkCollisions() {
        Arrow arrow = currentArrow;
        if (arrow == null || !arrow.isActive()) return;

        double ax = arrow.getX();
        double ay = arrow.getY();

        if (nearTarget != null && nearTarget.hitTest(ax, ay)) {
            arrow.deactivate();
            score += 1;
            hitX = ax; hitY = ay; hitTime = System.currentTimeMillis();
            notifyStats();
        } else if (farTarget != null && farTarget.hitTest(ax, ay)) {
            arrow.deactivate();
            score += 2;
            hitX = ax; hitY = ay; hitTime = System.currentTimeMillis();
            notifyStats();
        }
    }

    private void notifyStats() {
        if (onStatsChanged != null) {
            int s = score, sh = shots;
            Platform.runLater(() -> onStatsChanged.accept(s, sh));
        }
    }

    //делегация рендеринга

    private void render() {
        GraphicsContext gc = getGraphicsContext2D();

        renderer.drawBackground(gc);
        renderer.drawGuidelines(gc);
        renderer.drawPlayerZone(gc);
        renderer.drawPlayer(gc, player.getY());

        if (gameRunning) {
            if (nearTarget != null) renderer.drawTarget(gc, nearTarget, "#e74c3c", "#c0392b");
            if (farTarget != null)  renderer.drawTarget(gc, farTarget,  "#e67e22", "#d35400");
        }

        Arrow arrow = currentArrow;
        if (arrow != null && arrow.isActive()) {
            renderer.drawArrow(gc, arrow);
        }

        renderer.drawHitEffect(gc, hitX, hitY, hitTime);
        renderer.drawBorders(gc);

        if (gamePaused && gameRunning) renderer.drawPauseOverlay(gc);
        if (!gameRunning) renderer.drawStartMessage(gc);
    }
}
