package Visualisation.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class MscPane extends VBox {
    public MscPane(String name) {
        setPadding(new Insets(4));
        setSpacing(3);
        setStyle("-fx-border-color: #3A3A3A; -fx-border-width: 1; -fx-background-color: #1F1F1F;");

        Label label = new Label(name);
        label.setStyle("-fx-font-size: 9px; -fx-text-fill: #E6E6E6;");
        getChildren().add(label);
    }
}
