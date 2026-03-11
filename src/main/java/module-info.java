module ru.arssedot.spring {
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.fxml;
    requires java.logging;

    exports ru.arssedot.spring.model;
    exports ru.arssedot.spring.view;
    exports ru.arssedot.spring.server;
    exports ru.arssedot.spring.client;

    opens ru.arssedot.spring.client to javafx.fxml, javafx.graphics;
}
