package ru.arssedot.spring.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.input.KeyEvent;

import ru.arssedot.spring.model.Arrow;
import ru.arssedot.spring.model.Player;
import ru.arssedot.spring.model.Target;
import ru.arssedot.spring.view.GameRenderer;

import java.util.Objects;

@SuppressWarnings("unused")
public class GameController {

    private static final double NEAR_X = 380;
    private static final double FAR_X = 520;
    private static final double NEAR_RADIUS = 28;
    private static final double FAR_RADIUS = 14;
    private static final int FRAME_MS = 16;

    @FXML private Canvas canvas;
    @FXML private Label scoreLabel;
    @FXML private Label shotsLabel;
    @FXML private Label arrowSpeedLabel;
    @FXML private Label targetSpeedLabel;
    @FXML private Slider arrowSpeedSlider;
    @FXML private Slider targetSpeedSlider;
    @FXML private Button pauseButton;

    private GameRenderer renderer;
    private Player player;
    private volatile Target nearTarget;
    private volatile Target farTarget;
    private volatile Arrow currentArrow;

    private volatile int score;
    private volatile int shots;
    private volatile boolean running;
    private volatile boolean paused;
    private double arrowSpeed = 5.0;
    private double targetSpeed = 2.0;

    private Thread gameLoop;
    private volatile boolean renderPending;

    @FXML
    public void initialize() {
        Objects.requireNonNull(canvas, "FXML: canvas");
        Objects.requireNonNull(scoreLabel, "FXML: scoreLabel");
        Objects.requireNonNull(shotsLabel, "FXML: shotsLabel");
        Objects.requireNonNull(pauseButton, "FXML: pauseButton");
        Objects.requireNonNull(arrowSpeedSlider, "FXML: arrowSpeedSlider");
        Objects.requireNonNull(targetSpeedSlider, "FXML: targetSpeedSlider");

        renderer = new GameRenderer(canvas.getGraphicsContext2D());
        player = new Player(canvas.getHeight());

        setupSliders();
        setupKeyboard();

        renderer.drawIdleScreen(canvas.getWidth(), canvas.getHeight());
    }

    private void setupSliders() {
        arrowSpeedSlider.valueProperty().addListener((observable, oldVal, newVal) -> {
            arrowSpeed = round(newVal.doubleValue());
            arrowSpeedLabel.setText(String.valueOf(arrowSpeed));
        });

        targetSpeedSlider.valueProperty().addListener((observable, oldVal, newVal) -> {
            targetSpeed = round(newVal.doubleValue());
            targetSpeedLabel.setText(String.valueOf(targetSpeed));
            if (nearTarget != null) nearTarget.setSpeed(targetSpeed);
            if (farTarget != null) farTarget.setSpeed(targetSpeed * 2);
        });
    }

    private void setupKeyboard() {
        canvas.setFocusTraversable(true);

        canvas.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null) return;

            newScene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
            newScene.addEventFilter(KeyEvent.KEY_RELEASED, this::onKeyReleased);

            newScene.windowProperty().addListener((wObs, oldWindow, newWindow) -> {
                if (newWindow != null) {
                    newWindow.setOnCloseRequest(event -> {
                        onStop();
                        Platform.exit();
                    });
                }
            });
        });
    }

    @FXML
    private void onStart() {
        if (running) onStop();

        score = 0;
        shots = 0;
        player.reset(canvas.getHeight());
        currentArrow = null;
        running = true;
        paused = false;

        scoreLabel.setText("0");
        shotsLabel.setText("0");
        pauseButton.setText("\u23F8  Пауза");

        nearTarget = new Target(NEAR_X, NEAR_RADIUS, targetSpeed, canvas.getHeight());
        farTarget = new Target(FAR_X, FAR_RADIUS, targetSpeed * 2, canvas.getHeight());
        nearTarget.start();
        farTarget.start();

        startGameLoop();
        Platform.runLater(canvas::requestFocus);
    }

    @FXML
    private void onStop() {
        running = false;
        if (nearTarget != null) nearTarget.stop();
        if (farTarget != null) farTarget.stop();
        if (currentArrow != null) currentArrow.deactivate();
        if (gameLoop != null) gameLoop.interrupt();
        pauseButton.setText("\u23F8  Пауза");
        Platform.runLater(() -> renderer.drawIdleScreen(canvas.getWidth(), canvas.getHeight()));
    }

    @FXML
    private void onPause() {
        if (!running) return;

        paused = !paused;

        if (paused) {
            pauseButton.setText("\u25B6  Продолжить");
            if (nearTarget != null) nearTarget.pause();
            if (farTarget != null) farTarget.pause();
            if (currentArrow != null) currentArrow.pause();
        } else {
            pauseButton.setText("\u23F8  Пауза");
            if (nearTarget != null) nearTarget.resume();
            if (farTarget != null) farTarget.resume();
            if (currentArrow != null) currentArrow.resume();
        }
        Platform.runLater(canvas::requestFocus);
    }

    @FXML
    private void onShoot() {
        if (!running || paused) return;
        if (currentArrow != null && currentArrow.isActive()) return;

        shots++;
        shotsLabel.setText(String.valueOf(shots));

        currentArrow = new Arrow(
                Player.PLAYER_X + 20,
                player.getY(),
                arrowSpeed,
                canvas.getWidth() + 50
        );
        currentArrow.start();
        Platform.runLater(canvas::requestFocus);
    }

    private void onKeyPressed(KeyEvent e) {
        switch (e.getCode()) {
            case W, UP -> {
                player.setMoveUp(true);
                e.consume();
            }
            case S, DOWN -> {
                player.setMoveDown(true);
                e.consume();
            }
            case SPACE -> {
                onShoot();
                e.consume();
            }
            default -> { }
        }
    }

    private void onKeyReleased(KeyEvent e) {
        switch (e.getCode()) {
            case W, UP -> {
                player.setMoveUp(false);
                e.consume();
            }
            case S, DOWN -> {
                player.setMoveDown(false);
                e.consume();
            }
            default -> { }
        }
    }

    private void startGameLoop() {
        gameLoop = new Thread(() -> {
            while (running) {
                if (!paused) {
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
                sleep(FRAME_MS);
            }
        }, "GameLoop");
        gameLoop.setDaemon(true);
        gameLoop.start();
    }

    private void checkCollisions() {
        Arrow arrow = currentArrow;
        if (arrow == null || !arrow.isActive()) return;

        Target near = nearTarget;
        Target far = farTarget;
        if (near == null || far == null) return;

        double ax = arrow.getX();
        double ay = arrow.getY();

        if (near.hitTest(ax, ay)) {
            arrow.deactivate();
            score += 1;
            Platform.runLater(() -> scoreLabel.setText(String.valueOf(score)));
        } else if (far.hitTest(ax, ay)) {
            arrow.deactivate();
            score += 2;
            Platform.runLater(() -> scoreLabel.setText(String.valueOf(score)));
        }
    }

    private void render() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        renderer.clear(w, h);
        renderer.drawGuideline(NEAR_X, h, "#e74c3c");
        renderer.drawGuideline(FAR_X, h, "#e67e22");
        renderer.drawPlayerZone(h);
        renderer.drawPlayer(player.getY());

        if (nearTarget != null) renderer.drawTarget(nearTarget, "#e74c3c", "#c0392b");
        if (farTarget != null) renderer.drawTarget(farTarget, "#e67e22", "#d35400");

        Arrow arrow = currentArrow;
        if (arrow != null && arrow.isActive()) renderer.drawArrow(arrow);

        renderer.drawBorder(w, h);

        if (paused) renderer.drawPauseOverlay(w, h);
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    @SuppressWarnings("BusyWait")
    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
