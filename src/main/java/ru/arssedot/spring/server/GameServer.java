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
import java.util.logging.Logger;

public class GameServer {

    public static final int PORT = 4242;
    static final int MAX_PLAYERS = 4;
    static final int WIN_SCORE = 6;
    static final double FIELD_W = 600;
    static final double FIELD_H = 400;
    static final double ARROW_START_X = 90;
    static final double ARROW_SPEED = 6;
    static final double PLAYER_SPEED = 3;

    private static final Logger LOG = Logger.getLogger(GameServer.class.getName());

    final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    volatile boolean gameRunning;
    volatile boolean gamePaused;
    private volatile double targetSpeed = 2;

    private final Target nearTarget = new Target(380, 35, 2, FIELD_H);
    private final Target farTarget  = new Target(520, 22, 4, FIELD_H);

    public static void main(String[] args) {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tH:%1$tM:%1$tS] %4$s: %5$s%n");
        new GameServer().start();
    }

    public void start() {
        Thread loop = new Thread(this::gameLoop, "GameLoop");
        loop.setDaemon(true);
        loop.start();

        try (ServerSocket ss = new ServerSocket(PORT)) {
            LOG.info("Сервер запущен на порту " + PORT);
            while (true) {
                Socket socket = ss.accept();
                LOG.info("Новое подключение: " + socket.getRemoteSocketAddress());
                new ClientHandler(socket, this).start();
            }
        } catch (IOException e) {
            LOG.severe("Ошибка сервера: " + e.getMessage());
        }
    }

    synchronized boolean addClient(ClientHandler ch) {
        Player p = ch.player;
        if (clients.size() >= MAX_PLAYERS) {
            ch.send("ERROR Сервер полон (макс. " + MAX_PLAYERS + ")");
            return false;
        }
        for (ClientHandler c : clients) {
            if (c.player.getName().equalsIgnoreCase(p.getName())) {
                ch.send("ERROR Имя «" + p.getName() + "» уже занято");
                return false;
            }
        }
        p.setColorIndex(assignColor());
        p.setY(FIELD_H / 2);
        clients.add(ch);
        ch.send("OK");
        broadcast("MSG " + p.getName() + " подключился (" + clients.size() + "/" + MAX_PLAYERS + ")");
        LOG.info(p.getName() + " подключился (" + clients.size() + "/" + MAX_PLAYERS + ")");
        return true;
    }

    synchronized void removeClient(ClientHandler ch) {
        clients.remove(ch);
        broadcast("MSG " + ch.player.getName() + " отключился");
        LOG.info(ch.player.getName() + " отключился (" + clients.size() + "/" + MAX_PLAYERS + ")");
        if (gameRunning && clients.isEmpty()) {
            gameRunning = false;
            LOG.info("Все игроки вышли — игра остановлена");
        }
    }

    synchronized void handleReady(ClientHandler ch) {
        ch.player.setReady(true);
        broadcast("MSG " + ch.player.getName() + " готов");
        LOG.info(ch.player.getName() + " готов");
        checkAllReady();
    }

    synchronized void handlePause(ClientHandler ch) {
        if (!gameRunning || gamePaused) {
            return;
        }
        gamePaused = true;
        for (ClientHandler c : clients) c.player.setReady(false);
        broadcast("MSG " + ch.player.getName() + " поставил паузу");
        LOG.info(ch.player.getName() + " поставил паузу");
    }

    synchronized void handleShoot(ClientHandler ch) {
        if (!gameRunning || gamePaused) {
            return;
        }
        if (ch.player.shoot(ARROW_START_X)) {
            LOG.info(ch.player.getName() + " выстрелил (выстрел #" + ch.player.getShots() + ")");
        }
    }

    synchronized void handleSpeed(ClientHandler ch, double speed) {
        targetSpeed = Math.max(1, Math.min(5, speed));
        nearTarget.setSpeed(targetSpeed);
        farTarget.setSpeed(targetSpeed * 2);
        broadcast("MSG " + ch.player.getName() + " изменил скорость мишеней: " + (int) targetSpeed);
        LOG.info("Скорость мишеней: " + fmt(targetSpeed));
    }

    private synchronized void checkAllReady() {
        if (clients.isEmpty()) {
            return;
        }
        for (ClientHandler c : clients) {
            if (!c.player.isReady()) {
                return;
            }
        }
        if (gamePaused) {
            gamePaused = false;
            broadcast("MSG Игра продолжается!");
            LOG.info("Пауза снята");
        } else if (!gameRunning) {
            startGame();
        }
    }

    private void startGame() {
        for (ClientHandler c : clients) c.player.reset(FIELD_H / 2);
        nearTarget.resetY(FIELD_H);
        farTarget.resetY(FIELD_H);
        gameRunning = true;
        gamePaused = false;
        broadcast("MSG Игра началась!");
        LOG.info("Игра запущена (" + clients.size() + " игроков)");
    }

    @SuppressWarnings("BusyWait")
    private void gameLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            synchronized (this) {
                if (gameRunning && !gamePaused) {
                    nearTarget.move();
                    farTarget.move();
                    for (ClientHandler c : clients) {
                        c.player.move(15, FIELD_H - 15, PLAYER_SPEED);
                        c.player.getArrow().move(ARROW_SPEED);
                        if (c.player.getArrow().getX() > FIELD_W) {
                            c.player.getArrow().deactivate();
                        }
                    }
                    checkCollisions();
                }
            }
            if (!clients.isEmpty()) broadcastState();
            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void checkCollisions() {
        for (ClientHandler c : clients) {
            Arrow a = c.player.getArrow();
            if (!a.isActive()) continue;

            if (nearTarget.hitTest(a.getX(), a.getY())) {
                c.player.addScore(1);
                a.deactivate();
                broadcast("MSG " + c.player.getName() + " → ближняя мишень (+1)");
                LOG.info(c.player.getName() + " попал в ближнюю мишень (+1, счёт: " + c.player.getScore() + ")");
                checkWin(c.player);
            } else if (farTarget.hitTest(a.getX(), a.getY())) {
                c.player.addScore(2);
                a.deactivate();
                broadcast("MSG " + c.player.getName() + " → дальняя мишень (+2)");
                LOG.info(c.player.getName() + " попал в дальнюю мишень (+2, счёт: " + c.player.getScore() + ")");
                checkWin(c.player);
            }
        }
    }

    private void checkWin(Player winner) {
        if (winner.getScore() < WIN_SCORE) {
            return;
        }
        broadcast("WIN " + winner.getName());
        gameRunning = false;
        for (ClientHandler c : clients) c.player.setReady(false);
        LOG.info("ПОБЕДИТЕЛЬ: " + winner.getName() + " (счёт: " + winner.getScore() + ")");
    }

    void broadcast(String msg) {
        for (ClientHandler c : clients) c.send(msg);
    }

    private void broadcastState() {
        StringBuilder sb = new StringBuilder("STATE ")
                .append(gameRunning).append(' ')
                .append(gamePaused).append(' ')
                .append(fmt(nearTarget.getY())).append(' ')
                .append(fmt(farTarget.getY())).append(' ')
                .append(fmt(targetSpeed));

        for (ClientHandler c : clients) {
            Player p = c.player;
            Arrow a = p.getArrow();
            sb.append(' ').append(p.getName())
              .append(',').append(fmt(p.getY()))
              .append(',').append(p.getScore())
              .append(',').append(p.getShots())
              .append(',').append(fmt(a.getX()))
              .append(',').append(fmt(a.getY()))
              .append(',').append(p.getColorIndex())
              .append(',').append(p.isReady());
        }
        broadcast(sb.toString());
    }

    private int assignColor() {
        boolean[] used = new boolean[MAX_PLAYERS];
        for (ClientHandler c : clients) used[c.player.getColorIndex()] = true;
        for (int i = 0; i < MAX_PLAYERS; i++) {
            if (!used[i]) return i;
        }
        return 0;
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.1f", v);
    }
}
