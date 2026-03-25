package ru.arssedot.spring.server;

import ru.arssedot.spring.model.Player;
import ru.arssedot.spring.protocol.ClientCommand;
import ru.arssedot.spring.protocol.ServerMessage;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientHandler extends Thread {

    private final Socket socket;
    private final GameServer server;
    private ObjectOutputStream objectWriter;
    private boolean joined;

    final Player player = new Player();

    ClientHandler(Socket socket, GameServer server) {
        super("Client-" + socket.getRemoteSocketAddress());
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            objectWriter = new ObjectOutputStream(socket.getOutputStream());
            objectWriter.flush();
            ObjectInputStream objectReader = new ObjectInputStream(socket.getInputStream());
            Object obj;
            while ((obj = objectReader.readObject()) != null) {
                if (obj instanceof ClientCommand command) {
                    handleCommand(command);
                }
            }
        } catch (IOException | ClassNotFoundException ignored) {
        } finally {
            if (joined) {
                server.removeClient(this);
            }
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private void handleCommand(ClientCommand command) {
        switch (command.type) {
            case JOIN -> {
                player.setName(command.name);
                joined = server.addClient(this);
                if (!joined) {
                    try { socket.close(); } catch (IOException ignored) {}
                }
            }
            case READY    -> server.handleReady(this);
            case SHOOT    -> server.handleShoot(this);
            case PAUSE    -> server.handlePause(this);
            case UP_ON    -> player.setMovingUp(true);
            case UP_OFF   -> player.setMovingUp(false);
            case DOWN_ON  -> player.setMovingDown(true);
            case DOWN_OFF -> player.setMovingDown(false);
            case SPEED    -> server.handleSpeed(this, command.speed);
        }
    }

    synchronized void send(ServerMessage message) {
        if (objectWriter != null) {
            try {
                objectWriter.writeObject(message);
                objectWriter.reset();
            } catch (IOException ignored) {}
        }
    }
}
