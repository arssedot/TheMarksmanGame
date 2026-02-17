package ru.arssedot.spring.field;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.function.Consumer;

/**
 * Правая панель интерфейса: счёт, количество выстрелов,
 * слайдеры настройки скоростей и подсказки по управлению.
 */
public class ScorePanel extends VBox {

    private final Label scoreValue;
    private final Label shotsValue;

    public ScorePanel(Consumer<Double> onArrowSpeedChanged,
                      Consumer<Double> onTargetSpeedChanged) {

        Label title = label("МЕТКИЙ СТРЕЛОК", 15, true, "#f39c12");

        Label scoreLbl = label("Счёт игрока:", 14, false, "#bdc3c7");
        scoreValue = label("0", 34, true, "#2ecc71");

        Label shotsLbl = label("Выстрелов:", 14, false, "#bdc3c7");
        shotsValue = label("0", 34, true, "#e74c3c");

        Label sep1 = label("─────────", 12, false, "#34495e");
        Label nearInfo = label("● Ближняя цель: +1", 11, false, "#e74c3c");
        Label farInfo  = label("● Дальняя цель: +2", 11, false, "#e67e22");

        Label sep2 = label("─────────", 12, false, "#34495e");

        Label arrowSpeedLbl = label("Скорость стрелы:", 11, false, "#bdc3c7");
        Label arrowSpeedVal = label("5.0", 11, true, "#3498db");
        Slider arrowSlider = slider(2, 10, 5);
        arrowSlider.valueProperty().addListener((obs, oldV, newV) -> {
            double v = Math.round(newV.doubleValue() * 10.0) / 10.0;
            arrowSpeedVal.setText(String.valueOf(v));
            onArrowSpeedChanged.accept(v);
        });

        Label targetSpeedLbl = label("Скорость мишеней:", 11, false, "#bdc3c7");
        Label targetSpeedVal = label("2.0", 11, true, "#e67e22");
        Slider targetSlider = slider(0.5, 5, 2);
        targetSlider.valueProperty().addListener((obs, oldV, newV) -> {
            double v = Math.round(newV.doubleValue() * 10.0) / 10.0;
            targetSpeedVal.setText(String.valueOf(v));
            onTargetSpeedChanged.accept(v);
        });

        Label sep3 = label("─────────", 12, false, "#34495e");
        Label ctrl1 = label("W / S — движение", 10, false, "#7f8c8d");
        Label ctrl2 = label("Пробел — выстрел", 10, false, "#7f8c8d");

        Region spacer1 = new Region();
        spacer1.setPrefHeight(8);
        Region spacer2 = new Region();
        spacer2.setPrefHeight(3);

        HBox arrowRow  = row(arrowSpeedLbl, arrowSpeedVal);
        HBox targetRow = row(targetSpeedLbl, targetSpeedVal);

        getChildren().addAll(
                title, spacer1,
                scoreLbl, scoreValue,
                spacer2,
                shotsLbl, shotsValue,
                sep1, nearInfo, farInfo,
                sep2,
                arrowRow, arrowSlider,
                targetRow, targetSlider,
                sep3, ctrl1, ctrl2
        );
        setSpacing(6);
        setAlignment(Pos.TOP_CENTER);
        setPadding(new Insets(12));
        setPrefWidth(210);
        setStyle("-fx-background-color: #111827;" +
                 "-fx-border-color: #1e3050;" +
                 "-fx-border-width: 0 0 0 2;");
    }

    /** Обновление счёта и количества выстрелов (вызывается с FX-потока). */
    public void updateStats(int score, int shots) {
        scoreValue.setText(String.valueOf(score));
        shotsValue.setText(String.valueOf(shots));
    }

    /* ============ Фабричные методы ============ */

    private static Label label(String text, double size, boolean bold, String color) {
        Label lbl = new Label(text);
        lbl.setFont(Font.font("System", bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        lbl.setTextFill(Color.web(color));
        return lbl;
    }

    private static Slider slider(double min, double max, double value) {
        Slider s = new Slider(min, max, value);
        s.setShowTickLabels(false);
        s.setShowTickMarks(false);
        s.setPrefWidth(180);
        s.setStyle("-fx-control-inner-background: #1e3050; -fx-accent: #3498db;");
        s.setOnMousePressed(e -> e.consume());
        s.setOnMouseReleased(e -> e.consume());
        return s;
    }

    private static HBox row(Label... labels) {
        HBox box = new HBox(6, labels);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }
}
