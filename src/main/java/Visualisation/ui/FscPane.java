package Visualisation.ui;

import Visualisation.model.SimState;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class FscPane extends VBox {
    private static final double BAR_WIDTH   = 75.0;
    private static final String CCL_COLOR  = "#FF7F0E";
    private static final String LABEL_STYLE =
            "-fx-text-fill: #00FF88; -fx-font-size: 10px; -fx-font-family: 'Consolas'; -fx-font-weight: bold;" +
            "-fx-background-color: #0A1A0F; -fx-padding: 1 4 1 4;" +
            "-fx-border-color: #00FF88; -fx-border-width: 1; -fx-border-radius: 3; -fx-background-radius: 3;";

    private final String      fscName;
    private final ProgressBar ccl      = new ProgressBar(0.0);
    private final Label       cclLabel = new Label();

    public FscPane(String fscName) {
        this.fscName = fscName;

        setPadding(new Insets(4));
        setSpacing(3);
        setStyle("-fx-border-color: #3A3A3A; -fx-border-width: 1; -fx-background-color: #1F1F1F;");

        Label name = new Label(fscName);
        name.setStyle("-fx-font-size: 9px; -fx-text-fill: #E6E6E6;");

        initBar(ccl);

        cclLabel.setStyle(LABEL_STYLE);
        cclLabel.setVisible(false);

        HBox cclRow = new HBox(4, ccl, cclLabel);
        cclRow.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(name, cclRow);
    }

    /** Show or hide the exact CCL count overlaid on the bar. */
    public void setDebugMode(boolean debug) {
        cclLabel.setVisible(debug);
    }

    public void refresh(SimState state) {
        int current = state.getInventoryFSC(this.fscName);
        int max     = state.getInventoryFSCMax(this.fscName);
        this.ccl.setProgress(ratio(current, max));
        this.cclLabel.setText(current + "/" + max);
    }

    private static void initBar(ProgressBar bar) {
        bar.setPrefWidth(BAR_WIDTH);
        bar.setMaxHeight(10);
        bar.setPrefHeight(10);
        bar.setMinHeight(10);
        bar.setStyle("-fx-accent: " + CCL_COLOR + "; -fx-control-inner-background: #2A2A2A; -fx-box-border: #2A2A2A;");
    }

    private static double ratio(double value, double max) {
        if (max <= 0) { return 0.0; }
        double r = value / max;
        if (r < 0) { return 0.0; }
        if (r > 1) { return 1.0; }
        return r;
    }
}
