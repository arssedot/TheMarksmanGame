package ru.arssedot.spring;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import ru.arssedot.spring.field.GameField;
import ru.arssedot.spring.field.ScorePanel;

/**
 * Точка входа приложения «Меткий стрелок».
 * Собирает сцену: игровое поле, панель счёта и кнопки управления.
 */
public class MarksmanApp extends Application {

    private GameField gameField;
    private Button pauseBtn;

    @Override
    public void start(Stage stage) {
        gameField = new GameField(600, 420);

        ScorePanel scorePanel = new ScorePanel(
                gameField::setArrowSpeed,
                gameField::setTargetSpeed
        );
        gameField.setOnStatsChanged(scorePanel::updateStats);

        HBox buttonBar = createButtonBar();

        BorderPane root = new BorderPane();
        root.setCenter(gameField);
        root.setRight(scorePanel);
        root.setBottom(buttonBar);
        root.setStyle("-fx-background-color: #0a0e1a;");

        Scene scene = new Scene(root, 830, 550);
        scene.setFill(Color.web("#0a0e1a"));
        setupKeyboard(scene);

        stage.setTitle("Меткий стрелок");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

        stage.setOnCloseRequest(e -> {
            gameField.stopGame();
            Platform.exit();
            System.exit(0);
        });
    }

    /* ============ Клавиатура ============ */

    private void setupKeyboard(Scene scene) {
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.W || e.getCode() == KeyCode.UP)
                gameField.setMoveUp(true);
            if (e.getCode() == KeyCode.S || e.getCode() == KeyCode.DOWN)
                gameField.setMoveDown(true);
            if (e.getCode() == KeyCode.SPACE)
                gameField.shoot();
        });
        scene.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.W || e.getCode() == KeyCode.UP)
                gameField.setMoveUp(false);
            if (e.getCode() == KeyCode.S || e.getCode() == KeyCode.DOWN)
                gameField.setMoveDown(false);
        });
    }

    /* ============ Панель кнопок ============ */

    private HBox createButtonBar() {
        Button startBtn = styledButton("\u25B6  Начало игры", "#27ae60", "#2ecc71");
        Button stopBtn  = styledButton("\u25A0  Остановить",  "#c0392b", "#e74c3c");
        pauseBtn        = styledButton("\u23F8  Пауза",       "#2980b9", "#3498db");
        Button shootBtn = styledButton("\u2192  Выстрел",     "#d35400", "#e67e22");

        startBtn.setOnAction(e -> {
            gameField.startGame();
            pauseBtn.setText("\u23F8  Пауза");
            gameField.requestFocus();
        });

        stopBtn.setOnAction(e -> {
            gameField.stopGame();
            pauseBtn.setText("\u23F8  Пауза");
        });

        pauseBtn.setOnAction(e -> {
            if (gameField.isPaused()) {
                gameField.resumeGame();
                pauseBtn.setText("\u23F8  Пауза");
            } else {
                gameField.pauseGame();
                pauseBtn.setText("\u25B6  Продолжить");
            }
            gameField.requestFocus();
        });

        shootBtn.setOnAction(e -> {
            gameField.shoot();
            gameField.requestFocus();
        });

        HBox bar = new HBox(12, startBtn, stopBtn, pauseBtn, shootBtn);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(12));
        bar.setStyle("-fx-background-color: #111827;" +
                     "-fx-border-color: #1e3050;" +
                     "-fx-border-width: 2 0 0 0;");
        return bar;
    }

    private Button styledButton(String text, String baseColor, String hoverColor) {
        Button btn = new Button(text);
        String base = String.format(
                "-fx-background-color: %s; -fx-text-fill: white; -fx-font-size: 13px;" +
                "-fx-font-weight: bold; -fx-padding: 8 20; -fx-cursor: hand;" +
                "-fx-background-radius: 6;", baseColor);
        String hover = base.replace(baseColor, hoverColor);
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> btn.setStyle(hover));
        btn.setOnMouseExited(e -> btn.setStyle(base));
        return btn;
    }

    /* ============ Точка входа ============ */

    public static void main(String[] args) {
        launch(args);
    }
}
