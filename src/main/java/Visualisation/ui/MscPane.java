package Visualisation.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * JavaFX node pane for the Main Supply Centre.
 * Displays a simple label with the MSC name.
 *
 * @author 621349it Ies Timmerarends
 * @author 612348ih Isabel Hellebrekers
 * @author 631426ls Lena Stiebing
 * @author 661267eb Eeke Bavelaar
 */
public class MscPane extends VBox {

    /**
     * Construct an MscPane displaying the given name.
     *
     * @param name the label text to display
     */
    public MscPane(String name) {
        setPadding(new Insets(4));
        setSpacing(3);
        setStyle("-fx-border-color: #3A3A3A; -fx-border-width: 1; -fx-background-color: #1F1F1F;");

        Label label = new Label(name);
        label.setStyle("-fx-font-size: 9px; -fx-text-fill: #E6E6E6;");
        getChildren().add(label);
    }
}
