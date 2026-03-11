package ru.arssedot.spring.model;

public class Player {

    private String name;
    private double y;
    private int score;
    private int shots;
    private int colorIndex;
    private boolean ready;
    private final Arrow arrow = new Arrow();

    private volatile boolean movingUp;
    private volatile boolean movingDown;

    public void move(double minY, double maxY, double speed) {
        if (movingUp && y > minY) {
            y -= speed;
        }
        if (movingDown && y < maxY) {
            y += speed;
        }
    }

    public boolean shoot(double startX) {
        if (arrow.isActive()) {
            return false;
        }
        arrow.activate(startX, y);
        shots++;
        return true;
    }

    public void addScore(int points) {
        score += points;
    }

    public void reset(double centerY) {
        score = 0;
        shots = 0;
        arrow.deactivate();
        y = centerY;
    }

    public String  getName(){
        return name;
    }
    public void setName(String n) {
        this.name = n;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public int getScore() {
        return score;
    }

    public int getShots() {
        return shots;
    }

    public int getColorIndex() {
        return colorIndex;
    }

    public void setColorIndex(int i) {
        this.colorIndex = i;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean r) {
        this.ready = r;
    }

    public Arrow getArrow() {
        return arrow;
    }

    public void setMovingUp(boolean v) {
        this.movingUp = v;
    }

    public void setMovingDown(boolean v) {
        this.movingDown = v;
    }
}
