package ru.arssedot.spring.server;

import ru.arssedot.spring.model.Player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Logger;

public class ClientHandler extends Thread {

    private static final Logger LOG = Logger.getLogger(ClientHandler.class.getName());

    private final Socket socket;
    private final GameServer server;
    private PrintWriter out;
    private boolean joined;

    final Player player = new Player();

    ClientHandler(Socket socket, GameServer server) {
        super("Client-" + socket.getRemoteSocketAddress());
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()))) {
            out = new PrintWriter(socket.getOutputStream(), true);
            String line;
            while ((line = in.readLine()) != null) {
                handleCommand(line.trim());
            }
        } catch (IOException e) {
            LOG.info("соединение потеряно: " + player.getName());
        } finally {
            if (joined) server.removeClient(this);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void handleCommand(String cmd) {
        switch (cmd) {
            case "READY" -> server.handleReady(this);
            case "SHOOT" -> server.handleShoot(this);
            case "PAUSE" -> server.handlePause(this);
            case "UP_ON" -> player.setMovingUp(true);
            case "UP_OFF" -> player.setMovingUp(false);
            case "DOWN_ON" -> player.setMovingDown(true);
            case "DOWN_OFF" -> player.setMovingDown(false);
            default -> {
                if (cmd.startsWith("JOIN ")) {
                    player.setName(cmd.substring(5).trim());
                    joined = server.addClient(this);
                    if (!joined) {
                        try { socket.close(); } catch (IOException ignored) {}
                    }
                } else if (cmd.startsWith("SPEED ")) {
                    try {
                        double speed = Double.parseDouble(cmd.substring(6).trim());
                        server.handleSpeed(this, speed);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
    }

    synchronized void send(String msg) {
        if (out != null) out.println(msg);
    }
}
