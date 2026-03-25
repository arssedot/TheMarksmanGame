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

import ru.arssedot.spring.protocol.ClientCommand;
import ru.arssedot.spring.protocol.GameSnapshot;
import ru.arssedot.spring.protocol.PlayerState;
import ru.arssedot.spring.protocol.ServerMessage;
import ru.arssedot.spring.view.GameRenderer;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

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
    private ObjectOutputStream objectWriter;
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
                sendCommand(ClientCommand.speed(newVal.doubleValue()));
            }
        });
        speedSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            if (!isChanging && !updatingSpeedFromServer) {
                sendCommand(ClientCommand.speed(speedSlider.getValue()));
            }
        });

        render();
    }

    @FXML private void onConnect() { connect(); }
    @FXML private void onReady()   { sendCommand(ClientCommand.ready()); }
    @FXML private void onPause()   { sendCommand(ClientCommand.pause()); }
    @FXML private void onShoot()   { sendCommand(ClientCommand.shoot()); }

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
            objectWriter = new ObjectOutputStream(socket.getOutputStream());
            objectWriter.flush();
            sendCommand(ClientCommand.join(name));
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
            if (objectWriter != null) objectWriter.close();
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
        if (readerThread != null) {
            try {
                readerThread.join(2000);
            } catch (InterruptedException ignored) {}
        }
    }

    private void sendCommand(ClientCommand command) {
        if (objectWriter != null) {
            try {
                objectWriter.writeObject(command);
                objectWriter.reset();
            } catch (IOException ignored) {}
        }
    }

    private void listenServer() {
        try (ObjectInputStream objectReader = new ObjectInputStream(socket.getInputStream())) {
            Object obj;
            while ((obj = objectReader.readObject()) != null) {
                if (obj instanceof ServerMessage message) {
                    processServerMessage(message);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            Platform.runLater(() -> {
                statusLabel.setText("соединение потеряно");
                resetConnection();
            });
        }
    }

    private void processServerMessage(ServerMessage message) {
        switch (message.type) {
            case SNAPSHOT -> {
                applySnapshot(message.snapshot);
                Platform.runLater(() -> {
                    render();
                    updateInfoPanel();
                    updateSpeedSlider();
                });
            }
            case TEXT -> Platform.runLater(() -> statusLabel.setText(message.text));
            case WIN -> {
                winnerName = message.text;
                Platform.runLater(() -> {
                    statusLabel.setText("победитель: " + winnerName + "!");
                    render();
                });
            }
            case OK -> Platform.runLater(() -> statusLabel.setText("подключен. нажмите «готов»"));
            case ERROR -> {
                String errorText = message.text;
                Platform.runLater(() -> { statusLabel.setText(errorText); resetConnection(); });
            }
        }
    }

    private void applySnapshot(GameSnapshot snapshot) {
        gameRunning = snapshot.gameRunning();
        gamePaused = snapshot.gamePaused();
        nearY = snapshot.nearTargetY();
        farY = snapshot.farTargetY();
        currentTargetSpeed = snapshot.targetSpeed();
        if (gameRunning) {
            winnerName = null;
        }
        synchronized (players) {
            players.clear();
            for (PlayerState ps : snapshot.players()) {
                players.add(new PlayerInfo(
                        ps.name(), ps.y(), ps.score(), ps.shots(),
                        ps.arrowX(), ps.arrowY(), ps.colorIndex(), ps.ready()));
            }
        }
    }

    private void resetConnection() {
        connected = false;
        connectBtn.setDisable(false);
        hostField.setDisable(false);
        nameField.setDisable(false);
        setGameButtonsDisabled(true);
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
                    Color color = GameRenderer.PLAYER_COLORS[player.color() % GameRenderer.PLAYER_COLORS.length];
                    renderer.drawPlayer(player.y(), color);
                    if (player.arrowX() > 0) {
                        renderer.drawArrow(player.arrowX(), player.arrowY(), color);
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
                Color playerColor = GameRenderer.PLAYER_COLORS[player.color() % GameRenderer.PLAYER_COLORS.length];
                Label nameLabel = new Label("игрок: " + player.name());
                nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + toHex(playerColor) + ";");
                infoPanel.getChildren().addAll(
                        nameLabel,
                        new Label("счет: " + player.score()),
                        new Label("выстрелов: " + player.shots()),
                        new Separator());
            }
        }
    }

    private void onKeyPressed(KeyEvent event) {
        switch (event.getCode()) {
            case W, UP -> sendCommand(ClientCommand.upOn());
            case S, DOWN -> sendCommand(ClientCommand.downOn());
            case SPACE -> sendCommand(ClientCommand.shoot());
            default -> { return; }
        }
        event.consume();
    }

    private void onKeyReleased(KeyEvent event) {
        switch (event.getCode()) {
            case W, UP -> sendCommand(ClientCommand.upOff());
            case S, DOWN -> sendCommand(ClientCommand.downOff());
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
