package ru.arssedot.spring.view;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

public class GameRenderer {

    public static final double W = 600;
    public static final double H = 400;
    public static final Color[] PLAYER_COLORS = {
            Color.web("#ff6b6b"), Color.web("#54a0ff"),
            Color.web("#5cd85c"), Color.web("#ffa940")
    };

    private final GraphicsContext gc;

    public GameRenderer(GraphicsContext gc) {
        this.gc = gc;
    }

    public void clear() {
        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#0a1a2e")),
                new Stop(1, Color.web("#0d2d2d"))));
        gc.fillRect(0, 0, W, H);
    }

    public void drawGrid() {
        gc.setStroke(Color.rgb(255, 255, 255, 0.05));
        gc.setLineWidth(0.5);
        for (double x = 0; x < W; x += 40) gc.strokeLine(x, 0, x, H);
        for (double y = 0; y < H; y += 40) gc.strokeLine(0, y, W, y);
    }

    public void drawGuideLines(double nearX, double farX) {
        gc.setStroke(Color.rgb(255, 255, 255, 0.1));
        gc.setLineWidth(1);
        gc.setLineDashes(8, 4);
        gc.strokeLine(nearX, 0, nearX, H);
        gc.strokeLine(farX, 0, farX, H);
        gc.setLineDashes(null);
    }

    public void drawYellowBar() {
        gc.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#c4a000")),
                new Stop(1, Color.web("#e8c800"))));
        gc.fillRect(54, 0, 32, H);
        gc.setStroke(Color.web("#a08000"));
        gc.setLineWidth(1);
        gc.strokeRect(54, 0, 32, H);
    }

    public void drawTarget(double x, double y, double r, Color baseColor) {
        gc.setFill(Color.WHITE);
        gc.fillOval(x - r, y - r, r * 2, r * 2);

        double r2 = r * 0.75;
        gc.setFill(baseColor);
        gc.fillOval(x - r2, y - r2, r2 * 2, r2 * 2);

        double r3 = r * 0.5;
        gc.setFill(Color.WHITE);
        gc.fillOval(x - r3, y - r3, r3 * 2, r3 * 2);

        double r4 = r * 0.25;
        gc.setFill(baseColor);
        gc.fillOval(x - r4, y - r4, r4 * 2, r4 * 2);

        gc.setStroke(baseColor.darker());
        gc.setLineWidth(1.5);
        gc.strokeOval(x - r, y - r, r * 2, r * 2);
    }

    public void drawPlayer(double y, Color color) {
        double[] xs = {58, 58, 84};
        double[] ys = {y - 12, y + 12, y};
        gc.setFill(color);
        gc.fillPolygon(xs, ys, 3);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1.2);
        gc.strokePolygon(xs, ys, 3);
    }

    public void drawArrow(double x, double y, Color color) {
        gc.setStroke(color);
        gc.setLineWidth(2.5);
        gc.strokeLine(x - 25, y, x - 5, y);
        gc.setFill(color);
        gc.fillPolygon(
                new double[]{x, x - 8, x - 8},
                new double[]{y, y - 5, y + 5}, 3);
    }

    public void drawOverlay(String text) {
        gc.setFill(Color.rgb(0, 0, 0, 0.4));
        gc.fillRect(0, 0, W, H);

        double boxW = Math.max(text.length() * 16, 260);
        double boxH = 56;
        gc.setFill(Color.rgb(5, 15, 25, 0.8));
        gc.fillRoundRect((W - boxW) / 2, (H - boxH) / 2, boxW, boxH, 16, 16);
        gc.setStroke(Color.rgb(255, 255, 255, 0.12));
        gc.setLineWidth(1);
        gc.strokeRoundRect((W - boxW) / 2, (H - boxH) / 2, boxW, boxH, 16, 16);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("System", FontWeight.BOLD, 24));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText(text, W / 2, H / 2);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.BASELINE);
    }
}
