package ru.arssedot.spring.model;

public class Arrow {

    private double x;
    private double y;
    private boolean active;

    public void activate(double startX, double startY) {
        this.x = startX;
        this.y = startY;
        this.active = true;
    }

    public void move(double speed) {
        if (active) x += speed;
    }

    public void deactivate() {
        active = false;
        x = -1;
    }

    public double getX(){
        return x;
    }
    public double getY(){
        return y;
    }
    public boolean isActive(){
        return active;
    }
}
