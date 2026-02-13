package Visualisation.ui;

import Visualisation.model.SimState;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;

public class FscPane extends VBox {
    private static final double BAR_WIDTH = 90.0;
    private static final String CCL_COLOR = "#FF7F0E";

    private final String fscName;
    private final ProgressBar ccl = new ProgressBar(0.0);

    public FscPane(String fscName) {
        this.fscName = fscName;

        setPadding(new Insets(4));
        setSpacing(3);
        setStyle("-fx-border-color: #3A3A3A; -fx-border-width: 1; -fx-background-color: #1F1F1F;");

        Label name = new Label(fscName);
        name.setStyle("-fx-font-size: 9px; -fx-text-fill: #E6E6E6;");

        initBar(ccl);

        getChildren().addAll(name, ccl);
    }

    public void refresh(SimState state) {
        int max = state.getInventoryFSCMax(this.fscName);
        this.ccl.setProgress(ratio(state.getInventoryFSC(this.fscName), max));
    }

    private static void initBar(ProgressBar bar) {
        bar.setPrefWidth(BAR_WIDTH);
        bar.setMaxHeight(10);
        bar.setPrefHeight(10);
        bar.setMinHeight(10);
        bar.setStyle("-fx-accent: " + FscPane.CCL_COLOR + "; -fx-control-inner-background: #2A2A2A; -fx-box-border: #2A2A2A;");
    }

    private static double ratio(double value, double max) {
        if (max <= 0) return 0.0;
        double r = value / max;
        if (r < 0) return 0.0;
        if (r > 1) return 1.0;
        return r;
    }
}
