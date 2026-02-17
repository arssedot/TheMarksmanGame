package ru.arssedot.spring.model;

/**
 * Мишень, движущаяся вертикально по направляющей линии.
 * Анимация движения реализована на основе класса {@link Thread}.
 */
public class Target {

    private final double x;
    private volatile double y;
    private final double radius;
    private volatile double speed;
    private final double minY;
    private final double maxY;
    private volatile int direction = 1;
    private volatile boolean running;
    private volatile boolean paused;
    private Thread thread;

    public Target(double x, double radius, double speed, double fieldHeight) {
        this.x = x;
        this.radius = radius;
        this.speed = speed;
        this.minY = radius;
        this.maxY = fieldHeight - radius;
        this.y = fieldHeight / 2.0;
    }

    public void start() {
        if (running) return;
        running = true;
        paused = false;
        thread = new Thread(this::run, "Target-" + (int) x);
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        while (running) {
            if (!paused) {
                y += speed * direction;
                if (y >= maxY) {
                    y = maxY;
                    direction = -1;
                } else if (y <= minY) {
                    y = minY;
                    direction = 1;
                }
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    public void pause()  { paused = true; }
    public void resume() { paused = false; }

    public boolean hitTest(double px, double py) {
        double dx = px - x;
        double dy = py - y;
        return (dx * dx + dy * dy) <= (radius * radius);
    }

    public double getX()      { return x; }
    public double getY()      { return y; }
    public double getRadius() { return radius; }

    public void setSpeed(double speed) {
        this.speed = speed;
    }
}
