package Visualisation.ui;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/**
 * JavaFX control toolbar for the simulation visualiser.
 * Provides pause/play, reset, debug toggle, step navigation,
 * a speed slider, and a simulation clock display.
 *
 * @author 621349it Ies Timmerarends
 * @author 612348ih Isabel Hellebrekers
 * @author 631426ls Lena Stiebing
 * @author 661267eb Eeke Bavelaar
 */
public class ControlsPane extends HBox {
    private static final String BTN_DARK  = "-fx-background-color: #2A2A2A; -fx-text-fill: #E6E6E6; -fx-border-color: #3A3A3A;";
    private static final String BTN_GREEN = "-fx-background-color: #0D1F13; -fx-text-fill: #00FF88; -fx-border-color: #00FF88;";
    private static final String BTN_STEP  = "-fx-background-color: #1A2A1A; -fx-text-fill: #00FF88; -fx-border-color: #00FF8855;";

    private final Button pause    = new Button("Pause");
    private final Button reset    = new Button("Reset");
    private final Button debug    = new Button("Debug");
    private final Button prevStep = new Button("<< Prev");
    private final Button nextStep = new Button("Next >>");
    private final Slider speed    = new Slider(0.03, 0.5, 0.265);
    private final Label  clock    = new Label("Day 1 09:00");

    /**
     * Construct the controls toolbar with all buttons, speed slider, and clock.
     */
    public ControlsPane() {
        setSpacing(8);
        setPadding(new Insets(8));
        setStyle("-fx-background-color: #111111;");

        pause.setStyle(BTN_DARK);
        reset.setStyle(BTN_DARK);
        debug.setStyle(BTN_DARK);
        prevStep.setStyle(BTN_STEP);
        nextStep.setStyle(BTN_STEP);

        // Step buttons hidden until debug mode is enabled
        prevStep.setVisible(false);
        prevStep.setManaged(false);
        nextStep.setVisible(false);
        nextStep.setManaged(false);

        speed.setPrefWidth(140);
        speed.setShowTickMarks(false);
        speed.setShowTickLabels(false);
        speed.setStyle("-fx-control-inner-background: #1A1A1A;");
        Label speedLabel = new Label("Speed");
        speedLabel.setStyle("-fx-text-fill: #BDBDBD; -fx-padding: 0 4 0 8;");

        clock.setStyle("-fx-text-fill: #FF2A2A; -fx-font-size: 14px; -fx-font-family: 'Consolas';");
        clock.setEffect(new DropShadow(12, Color.color(1.0, 0.2, 0.2, 0.7)));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(pause, reset, debug, prevStep, nextStep, speedLabel, speed, spacer, clock);
    }

    /** Toggle debug button glow and show/hide step buttons. */
    public void setDebugMode(boolean active) {
        debug.setStyle(active ? BTN_GREEN : BTN_DARK);
        prevStep.setVisible(active);
        prevStep.setManaged(active);
        nextStep.setVisible(active);
        nextStep.setManaged(active);
    }

    /**
     * Register a handler for the pause/play button.
     *
     * @param handler the event handler to invoke on click
     */
    public void setOnPause(EventHandler<ActionEvent> handler) { pause.setOnAction(handler); }

    /**
     * Register a handler for the reset button.
     *
     * @param handler the event handler to invoke on click
     */
    public void setOnReset(EventHandler<ActionEvent> handler) { reset.setOnAction(handler); }

    /**
     * Register a handler for the debug toggle button.
     *
     * @param handler the event handler to invoke on click
     */
    public void setOnDebug(EventHandler<ActionEvent> handler) { debug.setOnAction(handler); }

    /**
     * Register a handler for the next-step button.
     *
     * @param handler the event handler to invoke on click
     */
    public void setOnNext(EventHandler<ActionEvent> handler)  { nextStep.setOnAction(handler); }

    /**
     * Register a handler for the previous-step button.
     *
     * @param handler the event handler to invoke on click
     */
    public void setOnPrev(EventHandler<ActionEvent> handler)  { prevStep.setOnAction(handler); }

    /**
     * Returns the speed slider control.
     *
     * @return the speed slider
     */
    public Slider speedSlider() { return speed; }

    /**
     * Set the simulation clock display text.
     *
     * @param text the clock label text, e.g. "Day 1 09:00"
     */
    public void setClockText(String text) { clock.setText(text); }

    /**
     * Update the pause/play button label to reflect the current paused state.
     *
     * @param paused true to show "Play", false to show "Pause"
     */
    public void setPaused(boolean paused) { pause.setText(paused ? "Play" : "Pause"); }
}
