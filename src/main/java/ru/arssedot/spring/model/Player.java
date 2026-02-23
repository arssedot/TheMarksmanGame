package ru.arssedot.spring.model;

public class Player {

    public static final double PLAYER_X = 50;
    private static final double SPEED = 4.0;

    private volatile double y;
    private volatile boolean moveUp;
    private volatile boolean moveDown;
    private final double minY;
    private final double maxY;

    public Player(double fieldHeight) {
        this.y = fieldHeight / 2.0;
        this.minY = 20;
        this.maxY = fieldHeight - 20;
    }

    public void update() {
        if (moveUp && y > minY) y -= SPEED;
        if (moveDown && y < maxY) y += SPEED;
    }

    public void reset(double fieldHeight) {
        this.y = fieldHeight / 2.0;
    }

    public double getY() {
        return y;
    }

    public void setMoveUp(boolean v) {
        moveUp = v;
    }

    public void setMoveDown(boolean v) {
        moveDown = v;
    }
}
