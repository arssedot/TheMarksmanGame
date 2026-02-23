module ru.arssedot.spring {
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.fxml;

    opens ru.arssedot.spring to javafx.fxml, javafx.graphics;
    opens ru.arssedot.spring.controller to javafx.fxml;

    exports ru.arssedot.spring;
    exports ru.arssedot.spring.model;
    exports ru.arssedot.spring.view;
    exports ru.arssedot.spring.controller;
}
