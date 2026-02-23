package ru.arssedot.spring.view;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import ru.arssedot.spring.model.Arrow;
import ru.arssedot.spring.model.Player;
import ru.arssedot.spring.model.Target;

public class GameRenderer {

    private static final Color BG_COLOR = Color.web("#131a2b");
    private static final Color GRID_COLOR = Color.web("#1e3050", 0.35);
    private static final Color BORDER_COLOR = Color.web("#1e3050");
    private static final Color ZONE_COLOR = Color.web("#ccb800", 0.2);
    private static final Color PLAYER_FILL = Color.web("#3498db");
    private static final Color PLAYER_LIGHT = Color.web("#5dade2", 0.6);
    private static final Color PLAYER_STROKE = Color.web("#2980b9");
    private static final Color ARROW_COLOR = Color.web("#e74c3c");

    private static final double GRID_STEP = 40;
    private static final double ARROW_LEN = 35;
    private static final double PLAYER_SIZE = 18;

    private final GraphicsContext gc;

    public GameRenderer(GraphicsContext gc) {
        this.gc = gc;
    }

    public void clear(double w, double h) {
        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, w, h);

        gc.setStroke(GRID_COLOR);
        gc.setLineWidth(0.5);
        gc.setLineDashes(null);
        for (double x = 0; x < w; x += GRID_STEP) gc.strokeLine(x, 0, x, h);
        for (double y = 0; y < h; y += GRID_STEP) gc.strokeLine(0, y, w, y);
    }

    public void drawGuideline(double x, double h, String color) {
        gc.setStroke(Color.web(color, 0.2));
        gc.setLineWidth(1.5);
        gc.setLineDashes(8, 12);
        gc.strokeLine(x, 0, x, h);
        gc.setLineDashes(null);
    }

    public void drawPlayerZone(double h) {
        double px = Player.PLAYER_X;
        gc.setFill(ZONE_COLOR);
        gc.fillRect(px - 15, 0, 30, h);
    }

    public void drawPlayer(double playerY) {
        double px = Player.PLAYER_X;
        double s = PLAYER_SIZE;

        double[] xs = {px + s, px - s * 0.7, px - s * 0.7};
        double[] ys = {playerY, playerY - s * 0.8, playerY + s * 0.8};

        gc.setFill(PLAYER_FILL);
        gc.fillPolygon(xs, ys, 3);

        gc.setFill(PLAYER_LIGHT);
        gc.fillPolygon(
                new double[]{px + s * 0.5, px - s * 0.3, px - s * 0.3},
                new double[]{playerY, playerY - s * 0.4, playerY + s * 0.4}, 3);

        gc.setStroke(PLAYER_STROKE);
        gc.setLineWidth(2);
        gc.strokePolygon(xs, ys, 3);
    }

    public void drawTarget(Target t, String primary, String secondary) {
        double tx = t.getX(), ty = t.getY(), r = t.getRadius();
        Color c1 = Color.web(primary), c2 = Color.web(secondary);

        double[] rings = {1.0, 0.75, 0.5, 0.25};
        Color[] fills = {Color.WHITE, c1, Color.WHITE, c2};

        for (int i = 0; i < rings.length; i++) {
            double cr = r * rings[i];
            gc.setFill(fills[i]);
            gc.fillOval(tx - cr, ty - cr, cr * 2, cr * 2);
        }

        gc.setStroke(Color.web(secondary, 0.8));
        gc.setLineWidth(1.5);
        gc.strokeOval(tx - r, ty - r, r * 2, r * 2);
    }

    public void drawArrow(Arrow a) {
        double ax = a.getX(), ay = a.getY();

        gc.setStroke(ARROW_COLOR);
        gc.setLineWidth(3);
        gc.strokeLine(ax - ARROW_LEN, ay, ax - 6, ay);

        gc.setFill(ARROW_COLOR);
        gc.fillPolygon(
                new double[]{ax, ax - 10, ax - 10},
                new double[]{ay, ay - 5, ay + 5}, 3);
    }

    public void drawBorder(double w, double h) {
        gc.setFill(BORDER_COLOR);
        gc.fillRect(0, 0, w, 3);
        gc.fillRect(0, h - 3, w, 3);
        gc.fillRect(0, 0, 3, h);
        gc.fillRect(w - 3, 0, 3, h);
    }

    public void drawPauseOverlay(double w, double h) {
        gc.setFill(Color.web("#000000", 0.55));
        gc.fillRect(0, 0, w, h);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("System", FontWeight.BOLD, 36));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("\u23F8  ПАУЗА", w / 2, h / 2 - 10);

        gc.setFill(Color.web("#bdc3c7"));
        gc.setFont(Font.font("System", FontWeight.NORMAL, 14));
        gc.fillText("Нажмите «Пауза» для продолжения", w / 2, h / 2 + 20);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    public void drawIdleScreen(double w, double h) {
        clear(w, h);
        drawBorder(w, h);

        gc.setFill(Color.web("#ecf0f1", 0.6));
        gc.setFont(Font.font("System", FontWeight.NORMAL, 20));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("Нажмите «\u25B6 Начало игры»", w / 2, h / 2);

        gc.setFill(Color.web("#7f8c8d", 0.7));
        gc.setFont(Font.font("System", FontWeight.NORMAL, 13));
        gc.fillText("W / S — прицеливание   |   Пробел — выстрел", w / 2, h / 2 + 30);
        gc.setTextAlign(TextAlignment.LEFT);
    }
}
