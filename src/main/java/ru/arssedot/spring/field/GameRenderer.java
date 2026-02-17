package ru.arssedot.spring.field;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import ru.arssedot.spring.model.Arrow;
import ru.arssedot.spring.model.Player;
import ru.arssedot.spring.model.Target;

/**
 * отвечает исключительно за отрисовку всех элементов на Canvas.
 * не содержит игровой логики - только визуализацию.
 */
public class GameRenderer {

    private static final double ARROW_LENGTH = 35;

    private final double w;
    private final double h;
    private final double nearX;
    private final double farX;

    public GameRenderer(double fieldWidth, double fieldHeight, double nearX, double farX) {
        this.w = fieldWidth;
        this.h = fieldHeight;
        this.nearX = nearX;
        this.farX = farX;
    }

    //фон с сеткой

    public void drawBackground(GraphicsContext gc) {
        gc.setFill(Color.web("#131a2b"));
        gc.fillRect(0, 0, w, h);

        gc.setStroke(Color.web("#1e3050", 0.35));
        gc.setLineWidth(0.5);
        gc.setLineDashes(null);
        for (double x = 0; x < w; x += 40) gc.strokeLine(x, 0, x, h);
        for (double y = 0; y < h; y += 40) gc.strokeLine(0, y, w, y);
    }

    // направляющие

    public void drawGuidelines(GraphicsContext gc) {
        gc.setLineWidth(1.5);
        gc.setLineDashes(8, 12);

        gc.setStroke(Color.web("#e74c3c", 0.2));
        gc.strokeLine(nearX, 0, nearX, h);

        gc.setStroke(Color.web("#e67e22", 0.2));
        gc.strokeLine(farX, 0, farX, h);

        gc.setLineDashes(null);
    }

    //область игрока

    public void drawPlayerZone(GraphicsContext gc) {
        double px = Player.PLAYER_X;
        LinearGradient strip = new LinearGradient(
                px - 15, 0, px + 15, 0, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#ccb800", 0.0)),
                new Stop(0.5, Color.web("#ccb800", 0.25)),
                new Stop(1, Color.web("#ccb800", 0.0)));
        gc.setFill(strip);
        gc.fillRect(px - 15, 0, 30, h);
    }

    //моделька игрока

    public void drawPlayer(GraphicsContext gc, double playerY) {
        double px = Player.PLAYER_X;
        double size = 18;

        gc.setFill(Color.web("#3498db"));
        gc.fillPolygon(
                new double[]{px + size, px - size * 0.7, px - size * 0.7},
                new double[]{playerY, playerY - size * 0.8, playerY + size * 0.8},
                3);

        gc.setFill(Color.web("#5dade2", 0.6));
        gc.fillPolygon(
                new double[]{px + size * 0.5, px - size * 0.3, px - size * 0.3},
                new double[]{playerY, playerY - size * 0.4, playerY + size * 0.4},
                3);

        gc.setStroke(Color.web("#2980b9"));
        gc.setLineWidth(2);
        gc.strokePolygon(
                new double[]{px + size, px - size * 0.7, px - size * 0.7},
                new double[]{playerY, playerY - size * 0.8, playerY + size * 0.8},
                3);
    }

    // мишень

    public void drawTarget(GraphicsContext gc, Target t, String color1, String color2) {
        double tx = t.getX();
        double ty = t.getY();
        double r = t.getRadius();

        double[] sizes = {1.0, 0.75, 0.5, 0.25};
        Color[] colors = {Color.WHITE, Color.web(color1), Color.WHITE, Color.web(color2)};
        for (int i = 0; i < sizes.length; i++) {
            double cr = r * sizes[i];
            gc.setFill(colors[i]);
            gc.fillOval(tx - cr, ty - cr, cr * 2, cr * 2);
        }

        gc.setStroke(Color.web(color2, 0.8));
        gc.setLineWidth(1.5);
        gc.strokeOval(tx - r, ty - r, r * 2, r * 2);
    }

    //стрела

    public void drawArrow(GraphicsContext gc, Arrow a) {
        double ax = a.getX();
        double ay = a.getY();

        double trailLen = ARROW_LENGTH * 2;
        LinearGradient trail = new LinearGradient(
                ax - trailLen, ay, ax, ay, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#e74c3c", 0.0)),
                new Stop(1, Color.web("#e74c3c", 0.3)));
        gc.setStroke(trail);
        gc.setLineWidth(2);
        gc.strokeLine(ax - trailLen, ay, ax - 6, ay);

        gc.setStroke(Color.web("#e74c3c"));
        gc.setLineWidth(3);
        gc.strokeLine(ax - ARROW_LENGTH, ay, ax - 6, ay);

        gc.setFill(Color.web("#e74c3c"));
        gc.fillPolygon(
                new double[]{ax, ax - 10, ax - 10},
                new double[]{ay, ay - 5, ay + 5},
                3);
    }

    //эффект попадания

    public void drawHitEffect(GraphicsContext gc, double hitX, double hitY, long hitTime) {
        long elapsed = System.currentTimeMillis() - hitTime;
        if (hitTime == 0 || elapsed > 500) return;

        double progress = elapsed / 500.0;
        double radius = 15 + progress * 35;
        double opacity = 1.0 - progress;

        gc.setFill(Color.web("#f1c40f", opacity * 0.4));
        gc.fillOval(hitX - radius, hitY - radius, radius * 2, radius * 2);

        gc.setStroke(Color.web("#f1c40f", opacity));
        gc.setLineWidth(2);
        gc.strokeOval(hitX - radius, hitY - radius, radius * 2, radius * 2);

        gc.setStroke(Color.web("#ffffff", opacity * 0.6));
        gc.setLineWidth(1);
        double r2 = radius * 0.6;
        gc.strokeOval(hitX - r2, hitY - r2, r2 * 2, r2 * 2);
    }

    //рамка поля

    public void drawBorders(GraphicsContext gc) {
        gc.setFill(Color.web("#1e3050"));
        gc.fillRect(0, 0, w, 3);
        gc.fillRect(0, h - 3, w, 3);
        gc.fillRect(0, 0, 3, h);
        gc.fillRect(w - 3, 0, 3, h);
    }

    // экран паузы

    public void drawPauseOverlay(GraphicsContext gc) {
        gc.setFill(Color.web("#000000", 0.55));
        gc.fillRect(0, 0, w, h);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("System", FontWeight.BOLD, 38));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("\u23F8  ПАУЗА", w / 2, h / 2 - 10);

        gc.setFill(Color.web("#bdc3c7"));
        gc.setFont(Font.font("System", FontWeight.NORMAL, 14));
        gc.fillText("Нажмите \"Пауза\" для продолжения", w / 2, h / 2 + 20);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    // стратовое сообщение

    public void drawStartMessage(GraphicsContext gc) {
        gc.setFill(Color.web("#ecf0f1", 0.6));
        gc.setFont(Font.font("System", FontWeight.NORMAL, 20));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("Нажмите \"\u25B6 Начало игры\" для старта", w / 2, h / 2);

        gc.setFill(Color.web("#7f8c8d", 0.7));
        gc.setFont(Font.font("System", FontWeight.NORMAL, 13));
        gc.fillText("W / S  или  \u2191 / \u2193 — прицеливание   |   space — выстрел", w / 2, h / 2 + 30);
        gc.setTextAlign(TextAlignment.LEFT);
    }
}
