package ru.arssedot.spring.model;

public class Target {

    private final double x;
    private final double radius;
    private double speed;
    private double y;
    private int direction = 1;
    private final double minY;
    private final double maxY;

    public Target(double x, double radius, double speed, double fieldHeight) {
        this.x = x;
        this.radius = radius;
        this.speed = speed;
        this.y = fieldHeight / 2;
        this.minY = radius;
        this.maxY = fieldHeight - radius;
    }

    public void move() {
        y += speed * direction;
        if (y < minY || y > maxY) {
            direction *= -1;
        }
    }

    public void resetY(double fieldHeight) {
        y = fieldHeight / 2;
        direction = 1;
    }

    public boolean hitTest(double px, double py) {
        double dx = px - x;
        double dy = py - y;
        return dx * dx + dy * dy < radius * radius;
    }

    public double getX() {
        return x;
    }
    public double getY() {
        return y;
    }
    public double getRadius() {
        return radius;
    }
    public double getSpeed() {
        return speed;
    }
    public void setSpeed(double s) {
        this.speed = s;
    }
}
