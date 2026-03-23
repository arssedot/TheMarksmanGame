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

public class GameClient extends Application {

    private static final int PORT = 4242;
    private static final double NEAR_X = 380, FAR_X = 520;
    private static final double NEAR_R = 35, FAR_R = 22;
    private static final double PLAYER_START_Y = GameRenderer.H / 2;

    private static final Color NEAR_TARGET_COLOR = Color.rgb(211, 47,  47);
    private static final Color FAR_TARGET_COLOR  = Color.rgb(240, 200, 0);

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
    private PrintWriter printWriter;
    private Thread readerThread;
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

        stage.setTitle("меткий стрелок - клиент");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.setOnCloseRequest(e -> {
            controller.disconnect();
            Platform.exit();
        });
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
            statusLabel.setText("заполните все поля");
            return;
        }
        if (name.contains(",") || name.contains(" ")) {
            statusLabel.setText("имя без пробелов и запятых");
            return;
        }
        try {
            socket = new Socket(host, PORT);
            printWriter = new PrintWriter(socket.getOutputStream(), true);
            printWriter.println("JOIN " + name);
            connected = true;
            setGameButtonsDisabled(false);
            connectBtn.setDisable(true);
            hostField.setDisable(true);
            nameField.setDisable(true);

            readerThread = new Thread(this::listenServer, "ServerReader");
            readerThread.start();
        } catch (IOException exception) {
            statusLabel.setText("ошибка: " + exception.getMessage());
        }
    }

    private void disconnect() {
        connected = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
        if (readerThread != null) {
            try {
                readerThread.join(2000);
            } catch (InterruptedException ignored) {}
        }
    }

    private void send(String cmd) {
        if (printWriter != null) {
            printWriter.println(cmd);
        }
    }

    private void listenServer() {
        try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(socket.getInputStream()))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                processMessage(line);
            }
        } catch (IOException e) {
            Platform.runLater(() -> {
                statusLabel.setText("соединение потеряно");
                resetConnection();
            });
        }
    }

    private void processMessage(String msg) {
        if (msg.startsWith("STATE ")) {
            parseState(msg);
            Platform.runLater(() -> {
                render();
                updateInfoPanel();
                updateSpeedSlider();
            });
        } else if (msg.startsWith("MSG ")) {
            String text = msg.substring(4);
            Platform.runLater(() -> statusLabel.setText(text));
        } else if (msg.startsWith("WIN ")) {
            winnerName = msg.substring(4);
            Platform.runLater(() -> {
                statusLabel.setText("победитель: " + winnerName + "!");
                render();
            });
        } else if ("OK".equals(msg)) {
            Platform.runLater(() -> statusLabel.setText("подключен. нажмите «готов»"));
        } else if (msg.startsWith("ERROR ")) {
            String text = msg.substring(6);
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

    private static final int ST_RUNNING = 1, ST_PAUSED = 2, ST_NEAR_Y = 3, ST_FAR_Y = 4,
                             ST_SPEED = 5, ST_PLAYERS_FROM = 6;

    private static final int PL_NAME = 0, PL_Y = 1, PL_SCORE = 2, PL_SHOTS = 3,
                             PL_ARROW_X = 4, PL_ARROW_Y = 5, PL_COLOR = 6, PL_READY = 7,
                             PL_FIELD_COUNT = 8;

    private void parseState(String line) {
        try {
            String[] parts = line.split(" ");
            if (parts.length < ST_PLAYERS_FROM) {
                return;
            }
            
            gameRunning = Boolean.parseBoolean(parts[ST_RUNNING]);
            gamePaused = Boolean.parseBoolean(parts[ST_PAUSED]);
            nearY = Double.parseDouble(parts[ST_NEAR_Y]);
            farY = Double.parseDouble(parts[ST_FAR_Y]);
            currentTargetSpeed = Double.parseDouble(parts[ST_SPEED]);
            if (gameRunning) {
                winnerName = null;
            }

            synchronized (players) {
                players.clear();
                for (int i = ST_PLAYERS_FROM; i < parts.length; i++) {
                    String[] fields = parts[i].split(",");
                    if (fields.length < PL_FIELD_COUNT) {
                        continue;
                    }

                    players.add(parsePlayer(fields));
                }
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private static PlayerInfo parsePlayer(String[] fields) {
        return new PlayerInfo(
                fields[PL_NAME],
                Double.parseDouble(fields[PL_Y]),
                Integer.parseInt(fields[PL_SCORE]),
                Integer.parseInt(fields[PL_SHOTS]),
                Double.parseDouble(fields[PL_ARROW_X]),
                Double.parseDouble(fields[PL_ARROW_Y]),
                Integer.parseInt(fields[PL_COLOR]),
                Boolean.parseBoolean(fields[PL_READY]));
    }

    private void updateSpeedSlider() {
        if (speedSlider.isPressed() || speedSlider.isValueChanging()) {
            return;
        }

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
        renderer.drawTarget(NEAR_X, nearY, NEAR_R, NEAR_TARGET_COLOR);
        renderer.drawTarget(FAR_X, farY, FAR_R, FAR_TARGET_COLOR);

        synchronized (players) {
            if (players.isEmpty()) {
                renderer.drawPlayer(PLAYER_START_Y, GameRenderer.PLAYER_COLORS[0]);
            } else {
                for (PlayerInfo player : players) {
                    Color color = GameRenderer.PLAYER_COLORS[player.color % GameRenderer.PLAYER_COLORS.length];
                    renderer.drawPlayer(player.y, color);
                    if (player.arrowX > 0) {
                        renderer.drawArrow(player.arrowX, player.arrowY, color);
                    }
                }
            }
        }

        if (!connected) {
            renderer.drawOverlay("подключитесь к серверу");
        } else if (winnerName != null && !gameRunning) {
            renderer.drawOverlay("победитель: " + winnerName + "!");
        } else if (gamePaused) {
            renderer.drawOverlay("пауза");
        } else if (!gameRunning) {
            renderer.drawOverlay("ожидание готовности...");
        }
    }

    private void updateInfoPanel() {
        infoPanel.getChildren().clear();
        synchronized (players) {
            for (PlayerInfo player : players) {
                Color playerColor = GameRenderer.PLAYER_COLORS[player.color % GameRenderer.PLAYER_COLORS.length];
                Label nameLabel = new Label("игрок: " + player.name);
                nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + toHex(playerColor) + ";");
                infoPanel.getChildren().addAll(
                        nameLabel,
                        new Label("счет: " + player.score),
                        new Label("выстрелов: " + player.shots),
                        new Separator());
            }
        }
    }

    private void onKeyPressed(KeyEvent event) {
        switch (event.getCode()) {
            case W, UP -> send("UP_ON");
            case S, DOWN -> send("DOWN_ON");
            case SPACE -> send("SHOOT");
            default -> { return; }
        }
        event.consume();
    }

    private void onKeyReleased(KeyEvent event) {
        switch (event.getCode()) {
            case W, UP -> send("UP_OFF");
            case S, DOWN -> send("DOWN_OFF");
            default -> { return; }
        }
        event.consume();
    }

    private void setGameButtonsDisabled(boolean disabled) {
        readyBtn.setDisable(disabled);
        pauseBtn.setDisable(disabled);
        shootBtn.setDisable(disabled);
        speedSlider.setDisable(disabled);
    }

    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
