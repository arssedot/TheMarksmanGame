package ru.arssedot.spring.protocol;

import java.io.Serializable;

public class ServerMessage implements Serializable {

    public enum Type { OK, ERROR, TEXT, SNAPSHOT, WIN }

    public final Type type;
    public final String text;
    public final GameSnapshot snapshot;

    private ServerMessage(Type type, String text, GameSnapshot snapshot) {
        this.type = type;
        this.text = text;
        this.snapshot = snapshot;
    }

    public static ServerMessage ok() {
        return new ServerMessage(Type.OK, null, null);
    }

    public static ServerMessage error(String message) {
        return new ServerMessage(Type.ERROR, message, null);
    }

    public static ServerMessage text(String message) {
        return new ServerMessage(Type.TEXT, message, null);
    }

    public static ServerMessage snapshot(GameSnapshot snapshot) {
        return new ServerMessage(Type.SNAPSHOT, null, snapshot);
    }

    public static ServerMessage win(String winnerName) {
        return new ServerMessage(Type.WIN, winnerName, null);
    }
}
