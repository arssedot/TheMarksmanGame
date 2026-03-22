package ru.arssedot.spring.server;

import ru.arssedot.spring.model.Player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler extends Thread {

    private final Socket socket;
    private final GameServer server;
    private PrintWriter printWriter;
    private boolean joined;

    final Player player = new Player();

    ClientHandler(Socket socket, GameServer server) {
        super("Client-" + socket.getRemoteSocketAddress());
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            printWriter = new PrintWriter(socket.getOutputStream(), true);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                handleCommand(line.trim());
            }
        } catch (IOException ignored) {
        } finally {
            if (joined) {
                server.removeClient(this);
            }
            try { 
                socket.close(); 
            } catch (IOException ignored) {}
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
        if (printWriter != null) {
            printWriter.println(msg);
        }
    }
}
