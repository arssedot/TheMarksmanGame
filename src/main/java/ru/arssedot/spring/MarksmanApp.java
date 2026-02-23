package ru.arssedot.spring;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class MarksmanApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(
                getClass().getResource("/ru/arssedot/spring/marksman.fxml")
        );

        Scene scene = new Scene(root, 830, 530);
        scene.setFill(Color.web("#0a0e1a"));

        stage.setTitle("Меткий стрелок");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
