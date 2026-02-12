package Visualisation.ui;

import Visualisation.model.SimState;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;

public class OperatingUnitPane extends VBox {
    private static final double BAR_WIDTH = 90.0;
    private static final String FW_COLOR = "#1F77B4";
    private static final String FUEL_COLOR = "#2CA02C";
    private static final String AMMO_COLOR = "#D62728";

    private final String ouName;
    private final ProgressBar fw = new ProgressBar(0.0);
    private final ProgressBar fuel = new ProgressBar(0.0);
    private final ProgressBar ammo = new ProgressBar(0.0);

    public OperatingUnitPane(String ouName) {
        this.ouName = ouName;

        setPadding(new Insets(4));
        setSpacing(3);
        setStyle("-fx-border-color: #3A3A3A; -fx-border-width: 1; -fx-background-color: #1F1F1F;");

        Label name = new Label(ouName);
        name.setStyle("-fx-font-size: 9px; -fx-text-fill: #E6E6E6;");

        initBar(fw, FW_COLOR);
        initBar(fuel, FUEL_COLOR);
        initBar(ammo, AMMO_COLOR);

        getChildren().addAll(name, fw, fuel, ammo);
    }

    public void refresh(SimState state) {
        double fwMax = state.getInventoryOUMax(this.ouName, "FW");
        double fuelMax = state.getInventoryOUMax(this.ouName, "FUEL");
        double ammoMax = state.getInventoryOUMax(this.ouName, "AMMO");

        this.fw.setProgress(ratio(state.getInventoryOU(this.ouName, "FW"), fwMax));
        this.fuel.setProgress(ratio(state.getInventoryOU(this.ouName, "FUEL"), fuelMax));
        this.ammo.setProgress(ratio(state.getInventoryOU(this.ouName, "AMMO"), ammoMax));
    }

    private static void initBar(ProgressBar bar, String color) {
        bar.setPrefWidth(BAR_WIDTH);
        bar.setMinHeight(10);
        bar.setPrefHeight(10);
        bar.setMaxHeight(10);
        bar.setStyle("-fx-accent: " + color + "; -fx-control-inner-background: #2A2A2A; -fx-box-border: #2A2A2A;");
    }

    private static double ratio(double value, double max) {
        if (max <= 0) return 0.0;
        double r = value / max;
        if (r < 0) return 0.0;
        if (r > 1) return 1.0;
        return r;
    }
}
