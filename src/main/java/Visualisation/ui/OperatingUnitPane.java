package Visualisation.ui;

import Visualisation.model.SimState;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class OperatingUnitPane extends VBox {
    private static final double BAR_WIDTH   = 75.0;
    private static final String FW_COLOR    = "#1F77B4";
    private static final String FUEL_COLOR  = "#2CA02C";
    private static final String AMMO_COLOR  = "#D62728";
    private static final String LABEL_STYLE =
            "-fx-text-fill: #00FF88; -fx-font-size: 10px; -fx-font-family: 'Consolas'; -fx-font-weight: bold;" +
            "-fx-background-color: #0A1A0F; -fx-padding: 1 4 1 4;" +
            "-fx-border-color: #00FF88; -fx-border-width: 1; -fx-border-radius: 3; -fx-background-radius: 3;";

    private final String      ouName;
    private final ProgressBar fw       = new ProgressBar(0.0);
    private final ProgressBar fuel     = new ProgressBar(0.0);
    private final ProgressBar ammo     = new ProgressBar(0.0);
    private final Label       fwLabel   = new Label();
    private final Label       fuelLabel = new Label();
    private final Label       ammoLabel = new Label();

    public OperatingUnitPane(String ouName) {
        this.ouName = ouName;

        setPadding(new Insets(4));
        setSpacing(3);
        setStyle("-fx-border-color: #3A3A3A; -fx-border-width: 1; -fx-background-color: #1F1F1F;");

        Label name = new Label(ouName);
        name.setStyle("-fx-font-size: 9px; -fx-text-fill: #E6E6E6;");

        initBar(fw,   FW_COLOR);
        initBar(fuel, FUEL_COLOR);
        initBar(ammo, AMMO_COLOR);

        for (Label lbl : new Label[]{fwLabel, fuelLabel, ammoLabel}) {
            lbl.setStyle(LABEL_STYLE);
            lbl.setVisible(false);
        }

        getChildren().addAll(name,
                makeBarRow(fw,   fwLabel),
                makeBarRow(fuel, fuelLabel),
                makeBarRow(ammo, ammoLabel));
    }

    private static HBox makeBarRow(ProgressBar bar, Label label) {
        HBox row = new HBox(4, bar, label);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** Show or hide the exact kg value labels for each product. */
    public void setDebugMode(boolean debug) {
        for (Label lbl : new Label[]{fwLabel, fuelLabel, ammoLabel}) {
            lbl.setVisible(debug);
        }
    }

    public void refresh(SimState state) {
        double fwMax   = state.getInventoryOUMax(this.ouName, "FW");
        double fuelMax = state.getInventoryOUMax(this.ouName, "FUEL");
        double ammoMax = state.getInventoryOUMax(this.ouName, "AMMO");
        double fwVal   = state.getInventoryOU(this.ouName, "FW");
        double fuelVal = state.getInventoryOU(this.ouName, "FUEL");
        double ammoVal = state.getInventoryOU(this.ouName, "AMMO");

        this.fw.setProgress(ratio(fwVal, fwMax));
        this.fuel.setProgress(ratio(fuelVal, fuelMax));
        this.ammo.setProgress(ratio(ammoVal, ammoMax));

        fwLabel.setText(fmt(fwVal));
        fuelLabel.setText(fmt(fuelVal));
        ammoLabel.setText(fmt(ammoVal));
    }

    /** Format kg value: exact if < 100 000, otherwise round to nearest k. */
    private static String fmt(double v) {
        long r = Math.round(v);
        if (r >= 100_000) {
            return (r / 1000) + "k";
        }
        return Long.toString(r);
    }

    private static void initBar(ProgressBar bar, String color) {
        bar.setPrefWidth(BAR_WIDTH);
        bar.setMinHeight(10);
        bar.setPrefHeight(10);
        bar.setMaxHeight(10);
        bar.setStyle("-fx-accent: " + color + "; -fx-control-inner-background: #2A2A2A; -fx-box-border: #2A2A2A;");
    }

    private static double ratio(double value, double max) {
        if (max <= 0) { return 0.0; }
        double r = value / max;
        if (r < 0) { return 0.0; }
        if (r > 1) { return 1.0; }
        return r;
    }
}
