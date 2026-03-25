package ru.arssedot.spring.protocol;

import java.io.Serializable;

public class ClientCommand implements Serializable {

    public enum Type { JOIN, READY, PAUSE, SHOOT, UP_ON, UP_OFF, DOWN_ON, DOWN_OFF, SPEED }

    public final Type type;
    public final String name;
    public final double speed;

    private ClientCommand(Type type, String name, double speed) {
        this.type = type;
        this.name = name;
        this.speed = speed;
    }

    public static ClientCommand join(String name) {
        return new ClientCommand(Type.JOIN, name, 0);
    }

    public static ClientCommand ready() {
        return new ClientCommand(Type.READY, null, 0);
    }

    public static ClientCommand pause() {
        return new ClientCommand(Type.PAUSE, null, 0);
    }

    public static ClientCommand shoot() {
        return new ClientCommand(Type.SHOOT, null, 0);
    }

    public static ClientCommand upOn() {
        return new ClientCommand(Type.UP_ON, null, 0);
    }

    public static ClientCommand upOff() {
        return new ClientCommand(Type.UP_OFF, null, 0);
    }

    public static ClientCommand downOn() {
        return new ClientCommand(Type.DOWN_ON, null, 0);
    }

    public static ClientCommand downOff() {
        return new ClientCommand(Type.DOWN_OFF, null, 0);
    }

    public static ClientCommand speed(double value) {
        return new ClientCommand(Type.SPEED, null, value);
    }
}
