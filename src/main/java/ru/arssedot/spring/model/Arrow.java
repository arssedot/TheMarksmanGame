package ru.arssedot.spring.model;

/**
 * Стрела, выпущенная игроком. Летит слева направо по прямой.
 * Анимация движения реализована на основе класса {@link Thread}.
 */
public class Arrow {

    private volatile double x;
    private final double y;
    private final double speed;
    private final double maxX;
    private volatile boolean active = true;
    private volatile boolean paused;
    private Thread thread;

    public Arrow(double startX, double startY, double speed, double maxX) {
        this.x = startX;
        this.y = startY;
        this.speed = speed;
        this.maxX = maxX;
    }

    public void start() {
        thread = new Thread(this::run, "Arrow");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        while (active && x < maxX) {
            if (!paused) {
                x += speed;
            }
            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        active = false;
    }

    public void deactivate() { active = false; }
    public void pause()      { paused = true; }
    public void resume()     { paused = false; }

    public double  getX()      { return x; }
    public double  getY()      { return y; }
    public boolean isActive()  { return active; }
}
