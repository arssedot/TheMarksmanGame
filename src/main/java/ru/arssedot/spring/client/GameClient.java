package ru.arssedot.spring.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import ru.arssedot.spring.view.GameRenderer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

public class GameClient extends Application {

    private static final Logger LOG = Logger.getLogger(GameClient.class.getName());
    private static final int PORT = 4242;
    private static final double NEAR_X = 380, FAR_X = 520;
    private static final double NEAR_R = 35, FAR_R = 22;

    @FXML private Canvas canvas;
    @FXML private VBox infoPanel;
    @FXML private Label statusLabel;
    @FXML private TextField hostField;
    @FXML private TextField nameField;
    @FXML private Button connectBtn;
    @FXML private Button readyBtn;
    @FXML private Button pauseBtn;
    @FXML private Button shootBtn;
    @FXML private Slider speedSlider;

    private GameRenderer renderer;
    private Socket socket;
    private PrintWriter out;
    private volatile boolean connected;
    private boolean updatingSpeedFromServer;

    private volatile boolean gameRunning;
    private volatile boolean gamePaused;
    private volatile double nearY = GameRenderer.H / 2;
    private volatile double farY  = GameRenderer.H / 2;
    private volatile double currentTargetSpeed = 2;
    private volatile String winnerName;
    private final List<PlayerInfo> players = new ArrayList<>();

    record PlayerInfo(String name, double y, int score, int shots,
                      double arrowX, double arrowY, int color, boolean ready) {}

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("game.fxml"));
        Parent root = loader.load();
        GameClient controller = loader.getController();

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, controller::onKeyPressed);
        scene.addEventFilter(KeyEvent.KEY_RELEASED, controller::onKeyReleased);

        stage.setTitle("Меткий стрелок — Клиент");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.setOnCloseRequest(e -> { controller.disconnect(); Platform.exit(); });
        stage.show();
    }

    @FXML
    private void initialize() {
        renderer = new GameRenderer(canvas.getGraphicsContext2D());

        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingSpeedFromServer && !speedSlider.isValueChanging()) {
                sendSpeed(newVal.doubleValue());
            }
        });
        speedSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            if (!isChanging && !updatingSpeedFromServer) {
                sendSpeed(speedSlider.getValue());
            }
        });

        render();
    }

    private void sendSpeed(double value) {
        send("SPEED " + String.format(Locale.US, "%.1f", value));
    }

    @FXML private void onConnect() { connect(); }
    @FXML private void onReady()   { send("READY"); }
    @FXML private void onPause()   { send("PAUSE"); }
    @FXML private void onShoot()   { send("SHOOT"); }

    private void connect() {
        if (connected) return;
        String host = hostField.getText().trim();
        String name = nameField.getText().trim();
        if (host.isEmpty() || name.isEmpty()) {
            statusLabel.setText("Заполните все поля");
            return;
        }
        if (name.contains(",") || name.contains(" ")) {
            statusLabel.setText("Имя без пробелов и запятых");
            return;
        }
        LOG.info("Подключение к " + host + ":" + PORT + " как «" + name + "»");
        try {
            socket = new Socket(host, PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            out.println("JOIN " + name);
            connected = true;
            setGameButtonsDisabled(false);
            connectBtn.setDisable(true);
            hostField.setDisable(true);
            nameField.setDisable(true);

            Thread reader = new Thread(this::listenServer, "ServerReader");
            reader.setDaemon(true);
            reader.start();
        } catch (IOException e) {
            LOG.warning("Ошибка подключения: " + e.getMessage());
            statusLabel.setText("Ошибка: " + e.getMessage());
        }
    }

    private void disconnect() {
        connected = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    private void send(String cmd) {
        if (out != null) out.println(cmd);
    }

    private void listenServer() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                processMessage(line);
            }
        } catch (IOException e) {
            Platform.runLater(() -> {
                statusLabel.setText("Соединение потеряно");
                resetConnection();
            });
        }
    }

    private void processMessage(String msg) {
        if (msg.startsWith("STATE ")) {
            parseState(msg);
            Platform.runLater(() -> { render(); updateInfoPanel(); updateSpeedSlider(); });
        } else if (msg.startsWith("MSG ")) {
            String text = msg.substring(4);
            LOG.info(text);
            Platform.runLater(() -> statusLabel.setText(text));
        } else if (msg.startsWith("WIN ")) {
            winnerName = msg.substring(4);
            LOG.info("Победитель: " + winnerName);
            Platform.runLater(() -> {
                statusLabel.setText("Победитель: " + winnerName + "!");
                render();
            });
        } else if ("OK".equals(msg)) {
            LOG.info("Подключён к серверу");
            Platform.runLater(() -> statusLabel.setText("Подключён. Нажмите «Готов»"));
        } else if (msg.startsWith("ERROR ")) {
            String text = msg.substring(6);
            LOG.warning("Ошибка: " + text);
            Platform.runLater(() -> { statusLabel.setText(text); resetConnection(); });
        }
    }

    private void resetConnection() {
        connected = false;
        connectBtn.setDisable(false);
        hostField.setDisable(false);
        nameField.setDisable(false);
        setGameButtonsDisabled(true);
    }

    private void parseState(String line) {
        try {
            String[] parts = line.split(" ");
            if (parts.length < 6) return;
            gameRunning = Boolean.parseBoolean(parts[1]);
            gamePaused  = Boolean.parseBoolean(parts[2]);
            nearY = Double.parseDouble(parts[3]);
            farY  = Double.parseDouble(parts[4]);
            currentTargetSpeed = Double.parseDouble(parts[5]);
            if (gameRunning) winnerName = null;

            synchronized (players) {
                players.clear();
                for (int i = 6; i < parts.length; i++) {
                    String[] f = parts[i].split(",");
                    if (f.length < 8) continue;
                    players.add(new PlayerInfo(
                            f[0], Double.parseDouble(f[1]),
                            Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                            Double.parseDouble(f[4]), Double.parseDouble(f[5]),
                            Integer.parseInt(f[6]), Boolean.parseBoolean(f[7])));
                }
            }
        } catch (NumberFormatException e) {
            LOG.warning("Ошибка разбора: " + e.getMessage());
        }
    }

    private void updateSpeedSlider() {
        if (speedSlider.isPressed() || speedSlider.isValueChanging()) return;
        if (Math.abs(speedSlider.getValue() - currentTargetSpeed) > 0.1) {
            updatingSpeedFromServer = true;
            speedSlider.setValue(currentTargetSpeed);
            updatingSpeedFromServer = false;
        }
    }

    private void render() {
        renderer.clear();
        renderer.drawGrid();
        renderer.drawGuideLines(NEAR_X, FAR_X);
        renderer.drawYellowBar();
        renderer.drawTarget(NEAR_X, nearY, NEAR_R, Color.web("#d32f2f"));
        renderer.drawTarget(FAR_X, farY, FAR_R, Color.web("#f0c800"));

        synchronized (players) {
            for (PlayerInfo p : players) {
                Color color = GameRenderer.PLAYER_COLORS[p.color % GameRenderer.PLAYER_COLORS.length];
                renderer.drawPlayer(p.y, color);
                if (p.arrowX > 0) renderer.drawArrow(p.arrowX, p.arrowY, color);
            }
        }

        if (!connected) {
            renderer.drawOverlay("Подключитесь к серверу");
        } else if (winnerName != null && !gameRunning) {
            renderer.drawOverlay("Победитель: " + winnerName + "!");
        } else if (gamePaused) {
            renderer.drawOverlay("ПАУЗА");
        } else if (!gameRunning) {
            renderer.drawOverlay("Ожидание готовности...");
        }
    }

    private void updateInfoPanel() {
        infoPanel.getChildren().clear();
        synchronized (players) {
            for (PlayerInfo p : players) {
                Color c = GameRenderer.PLAYER_COLORS[p.color % GameRenderer.PLAYER_COLORS.length];
                Label nameLabel = new Label("Игрок: " + p.name + (p.ready ? " ✓" : ""));
                nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + toHex(c) + ";");
                infoPanel.getChildren().addAll(
                        nameLabel,
                        new Label("Счёт: " + p.score),
                        new Label("Выстрелов: " + p.shots),
                        new Separator());
            }
        }
    }

    private void onKeyPressed(KeyEvent e) {
        switch (e.getCode()) {
            case W, UP -> send("UP_ON");
            case S, DOWN -> send("DOWN_ON");
            case SPACE -> send("SHOOT");
            default -> { }
        }
    }

    private void onKeyReleased(KeyEvent e) {
        switch (e.getCode()) {
            case W, UP -> send("UP_OFF");
            case S, DOWN -> send("DOWN_OFF");
            default -> {}
        }
    }

    private void setGameButtonsDisabled(boolean disabled) {
        readyBtn.setDisable(disabled);
        pauseBtn.setDisable(disabled);
        shootBtn.setDisable(disabled);
        speedSlider.setDisable(disabled);
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x",
                (int) (c.getRed() * 255),
                (int) (c.getGreen() * 255),
                (int) (c.getBlue() * 255));
    }

    public static void main(String[] args) {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tH:%1$tM:%1$tS] %4$s: %5$s%n");
        launch(args);
    }
}
