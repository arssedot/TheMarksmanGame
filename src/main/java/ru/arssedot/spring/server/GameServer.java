package ru.arssedot.spring.server;

import ru.arssedot.spring.model.Arrow;
import ru.arssedot.spring.model.Player;
import ru.arssedot.spring.model.Target;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameServer {

    public static final int PORT = 4242;
    static final int MAX_PLAYERS = 4;
    static final int WIN_SCORE = 6;
    static final double FIELD_W = 600;
    static final double FIELD_H = 400;
    static final double ARROW_START_X = 90;
    static final double ARROW_SPEED = 6;
    static final double PLAYER_SPEED = 3;

    final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    volatile boolean gameRunning;
    volatile boolean gamePaused;
    private ClientHandler pausedBy;
    private volatile double targetSpeed = 2;

    private final Target nearTarget = new Target(380, 35, 2, FIELD_H);
    private final Target farTarget  = new Target(520, 22, 4, FIELD_H);

    public static void main(String[] args) {
        new GameServer().start();
    }

    public void start() {
        Thread gameLoopThread = new Thread(this::gameLoop, "GameLoop");
        gameLoopThread.start();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(socket, this).start();
            }
        } catch (IOException ignored) {
        }
    }

    synchronized boolean addClient(ClientHandler clientHandler) {
        Player player = clientHandler.player;
        if (clients.size() >= MAX_PLAYERS) {
            clientHandler.send("сервер полон (макс. " + MAX_PLAYERS + ")");
            return false;
        }
        for (ClientHandler other : clients) {
            if (other.player.getName().equalsIgnoreCase(player.getName())) {
                clientHandler.send("имя «" + player.getName() + "» уже занято");
                return false;
            }
        }
        player.setColorIndex(assignColor());
        player.setY(FIELD_H / 2);
        clients.add(clientHandler);
        clientHandler.send("OK");
        broadcast("MSG " + player.getName() + " подключился (" + clients.size() + "/" + MAX_PLAYERS + ")");
        return true;
    }

    synchronized void removeClient(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        broadcast("MSG " + clientHandler.player.getName() + " отключился");
        if (gamePaused && clientHandler == pausedBy) {
            gamePaused = false;
            pausedBy = null;
            broadcast("MSG пауза снята (игрок отключился)");
            notifyAll();
        }
        if (gameRunning && clients.isEmpty()) {
            gameRunning = false;
            gamePaused = false;
            pausedBy = null;
            notifyAll();
        }
    }

    synchronized void handleReady(ClientHandler clientHandler) {
        if (gameRunning) {
            return;
        }
        clientHandler.player.setReady(true);
        broadcast("MSG " + clientHandler.player.getName() + " готов");
        checkAllReady();
    }

    synchronized void handlePause(ClientHandler clientHandler) {
        if (!gameRunning) {
            return;
        }
        if (!gamePaused) {
            gamePaused = true;
            pausedBy = clientHandler;
            broadcast("MSG " + clientHandler.player.getName() + " поставил паузу");
            broadcastState();
        } else if (clientHandler == pausedBy) {
            gamePaused = false;
            pausedBy = null;
            broadcast("MSG " + clientHandler.player.getName() + " снял паузу");
            notifyAll();
        }
    }

    synchronized void handleShoot(ClientHandler clientHandler) {
        if (!gameRunning || gamePaused) {
            return;
        }
        clientHandler.player.shoot(ARROW_START_X);
    }

    synchronized void handleSpeed(ClientHandler clientHandler, double speed) {
        targetSpeed = Math.max(1, Math.min(5, speed));
        nearTarget.setSpeed(targetSpeed);
        farTarget.setSpeed(targetSpeed * 2);
        broadcast("MSG " + clientHandler.player.getName() + " изменил скорость мишеней: " + (int) targetSpeed);
    }

    private synchronized void checkAllReady() {
        if (clients.isEmpty() || gameRunning) {
            return;
        }
        for (ClientHandler client : clients) {
            if (!client.player.isReady()) {
                return;
            }
        }
        startGame();
    }

    private void startGame() {
        for (ClientHandler client : clients) {
            client.player.reset(FIELD_H / 2);
        }
        nearTarget.resetY(FIELD_H);
        farTarget.resetY(FIELD_H);
        gameRunning = true;
        gamePaused = false;
        pausedBy = null;
        broadcast("MSG игра началась");
        notifyAll();
    }

    @SuppressWarnings("BusyWait")
    private void gameLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            synchronized (this) {
                if (gamePaused) {
                    while (gamePaused) {
                        try {
                            wait();
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
                if (gameRunning) {
                    nearTarget.move();
                    farTarget.move();
                    for (ClientHandler client : clients) {
                        client.player.move(15, FIELD_H - 15, PLAYER_SPEED);
                        client.player.getArrow().move(ARROW_SPEED);
                        if (client.player.getArrow().getX() > FIELD_W) {
                            client.player.getArrow().deactivate();
                        }
                    }
                    checkCollisions();
                }
            }
            if (!clients.isEmpty()) {
                broadcastState();
            }
            try {
                Thread.sleep(16);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void checkCollisions() {
        for (ClientHandler client : clients) {
            Arrow arrow = client.player.getArrow();
            if (!arrow.isActive()) {
                continue;
            }

            if (nearTarget.hitTest(arrow.getX(), arrow.getY())) {
                client.player.addScore(1);
                arrow.deactivate();
                broadcast("MSG " + client.player.getName() + " -> ближняя мишень (+1)");
                checkWin(client.player);
            } else if (farTarget.hitTest(arrow.getX(), arrow.getY())) {
                client.player.addScore(2);
                arrow.deactivate();
                broadcast("MSG " + client.player.getName() + " -> дальняя мишень (+2)");
                checkWin(client.player);
            }
        }
    }

    private void checkWin(Player winner) {
        if (winner.getScore() < WIN_SCORE) {
            return;
        }

        broadcast("WIN " + winner.getName());
        gameRunning = false;
        gamePaused = false;
        pausedBy = null;

        for (ClientHandler client : clients) {
            client.player.setReady(false);
        }
        notifyAll();
    }

    void broadcast(String msg) {
        for (ClientHandler client : clients) {
            client.send(msg);
        }
    }

    private void broadcastState() {
        StringBuilder stateBuilder = new StringBuilder("STATE ")
                .append(gameRunning).append(' ')
                .append(gamePaused).append(' ')
                .append(fmt(nearTarget.getY())).append(' ')
                .append(fmt(farTarget.getY())).append(' ')
                .append(fmt(targetSpeed));

        for (ClientHandler client : clients) {
            Player player = client.player;
            Arrow arrow = player.getArrow();
            stateBuilder.append(' ').append(player.getName())
              .append(',').append(fmt(player.getY()))
              .append(',').append(player.getScore())
              .append(',').append(player.getShots())
              .append(',').append(fmt(arrow.getX()))
              .append(',').append(fmt(arrow.getY()))
              .append(',').append(player.getColorIndex())
              .append(',').append(player.isReady());
        }
        broadcast(stateBuilder.toString());
    }

    private int assignColor() {
        boolean[] used = new boolean[MAX_PLAYERS];
        for (ClientHandler client : clients) {
            used[client.player.getColorIndex()] = true;
        }
        for (int colorIndex = 0; colorIndex < MAX_PLAYERS; colorIndex++) {
            if (!used[colorIndex]) {
                return colorIndex;
            }
        }
        return 0;
    }

    private static String fmt(double value) {
        return String.format(Locale.US, "%.1f", value);
    }
}
