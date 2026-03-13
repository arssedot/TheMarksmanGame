package ru.arssedot.spring.server;

import ru.arssedot.spring.model.Arrow;
import ru.arssedot.spring.model.Player;
import ru.arssedot.spring.model.Target;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

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
    private ClientHandler pausedBy;
    private volatile double targetSpeed = 2;

    private final Target nearTarget = new Target(380, 35, 2, FIELD_H);
    private final Target farTarget  = new Target(520, 22, 4, FIELD_H);

    public static void main(String[] args) {
        setupLogging();
        new GameServer().start();
    }

    public void start() {
        Thread loop = new Thread(this::gameLoop, "GameLoop");
        loop.start();

        try (ServerSocket ss = new ServerSocket(PORT)) {
            LOG.info("сервер запущен на порту " + PORT);
            while (true) {
                Socket socket = ss.accept();
                LOG.info("новое подключение: " + socket.getRemoteSocketAddress());
                new ClientHandler(socket, this).start();
            }
        } catch (IOException e) {
            LOG.severe("ошибка сервера: " + e.getMessage());
        }
    }

    synchronized boolean addClient(ClientHandler ch) {
        Player p = ch.player;
        if (clients.size() >= MAX_PLAYERS) {
            ch.send("ERROR сервер полон (макс. " + MAX_PLAYERS + ")");
            return false;
        }
        for (ClientHandler c : clients) {
            if (c.player.getName().equalsIgnoreCase(p.getName())) {
                ch.send("ERROR имя «" + p.getName() + "» уже занято");
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
        if (gamePaused && ch == pausedBy) {
            gamePaused = false;
            pausedBy = null;
            broadcast("MSG пауза снята (игрок отключился)");
            LOG.info("пауза снята (поставивший паузу отключился)");
            notifyAll();
        }
        if (gameRunning && clients.isEmpty()) {
            gameRunning = false;
            gamePaused = false;
            pausedBy = null;
            LOG.info("все игроки вышли - игра остановлена");
            notifyAll();
        }
    }

    synchronized void handleReady(ClientHandler ch) {
        if (gameRunning) return;
        ch.player.setReady(true);
        broadcast("MSG " + ch.player.getName() + " готов");
        LOG.info(ch.player.getName() + " готов");
        checkAllReady();
    }

    synchronized void handlePause(ClientHandler ch) {
        if (!gameRunning) return;
        if (!gamePaused) {
            gamePaused = true;
            pausedBy = ch;
            broadcast("MSG " + ch.player.getName() + " поставил паузу");
            LOG.info(ch.player.getName() + " поставил паузу");
            broadcastState();
        } else if (ch == pausedBy) {
            gamePaused = false;
            pausedBy = null;
            broadcast("MSG " + ch.player.getName() + " снял паузу");
            LOG.info(ch.player.getName() + " снял паузу");
            notifyAll();
        }
    }

    synchronized void handleShoot(ClientHandler ch) {
        if (!gameRunning || gamePaused) return;
        if (ch.player.shoot(ARROW_START_X)) {
            LOG.info(ch.player.getName() + " выстрелил (выстрел #" + ch.player.getShots() + ")");
        }
    }

    synchronized void handleSpeed(ClientHandler ch, double speed) {
        targetSpeed = Math.max(1, Math.min(5, speed));
        nearTarget.setSpeed(targetSpeed);
        farTarget.setSpeed(targetSpeed * 2);
        broadcast("MSG " + ch.player.getName() + " изменил скорость мишеней: " + (int) targetSpeed);
        LOG.info("скорость мишеней: " + fmt(targetSpeed));
    }

    private synchronized void checkAllReady() {
        if (clients.isEmpty() || gameRunning) return;
        for (ClientHandler c : clients) {
            if (!c.player.isReady()) return;
        }
        startGame();
    }

    private void startGame() {
        for (ClientHandler c : clients) c.player.reset(FIELD_H / 2);
        nearTarget.resetY(FIELD_H);
        farTarget.resetY(FIELD_H);
        gameRunning = true;
        gamePaused = false;
        pausedBy = null;
        broadcast("MSG игра началась!");
        LOG.info("игра запущена (" + clients.size() + " игроков)");
        notifyAll();
    }

    @SuppressWarnings("BusyWait")
    private void gameLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            synchronized (this) {
                if (gamePaused) {
                    LOG.info("игровой цикл приостановлен");
                    while (gamePaused) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    LOG.info("игровой цикл возобновлен");
                }
                if (gameRunning) {
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
                broadcast("MSG " + c.player.getName() + " -> ближняя мишень (+1)");
                LOG.info(c.player.getName() + " попал в ближнюю мишень (+1, счет: " + c.player.getScore() + ")");
                checkWin(c.player);
            } else if (farTarget.hitTest(a.getX(), a.getY())) {
                c.player.addScore(2);
                a.deactivate();
                broadcast("MSG " + c.player.getName() + " -> дальняя мишень (+2)");
                LOG.info(c.player.getName() + " попал в дальнюю мишень (+2, счет: " + c.player.getScore() + ")");
                checkWin(c.player);
            }
        }
    }

    private void checkWin(Player winner) {
        if (winner.getScore() < WIN_SCORE) return;
        broadcast("WIN " + winner.getName());
        gameRunning = false;
        gamePaused = false;
        pausedBy = null;
        for (ClientHandler c : clients) c.player.setReady(false);
        LOG.info("победитель: " + winner.getName() + " (счет: " + winner.getScore() + ")");
        notifyAll();
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

    private static void setupLogging() {
        Logger root = Logger.getLogger("");
        for (Handler h : root.getHandlers()) root.removeHandler(h);
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        StreamHandler handler = new StreamHandler(System.out, new Formatter() {
            @Override
            public String format(LogRecord r) {
                String time = LocalTime.now().format(timeFmt);
                return "[" + time + "] " + r.getLevel() + ": " + r.getMessage() + "\n";
            }
        }) {
            @Override
            public synchronized void publish(LogRecord r) {
                super.publish(r);
                flush();
            }
        };
        root.addHandler(handler);
    }
}
