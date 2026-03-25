module ru.arssedot.spring {
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.fxml;

    exports ru.arssedot.spring.model;
    exports ru.arssedot.spring.view;
    exports ru.arssedot.spring.server;
    exports ru.arssedot.spring.client;
    exports ru.arssedot.spring.protocol;

    opens ru.arssedot.spring.client to javafx.fxml, javafx.graphics;
}
