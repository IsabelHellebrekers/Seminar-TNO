package Visualisation;

import DataUtils.InstanceCreator;
import Deterministic.CapacitatedResupplyMILP;
import Objects.Instance;
import Objects.OperatingUnit;
import Objects.Result;
import Visualisation.model.DeliveryEvent;
import Visualisation.model.DemandEvent;
import Visualisation.model.Event;
import Visualisation.model.FSCDeliveryEvent;
import Visualisation.model.SimState;
import Visualisation.ui.ControlsPane;
import Visualisation.ui.GraphPane;
import javafx.application.Application;
import javafx.animation.AnimationTimer;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.geometry.Pos;
import javafx.stage.Stage;
import javafx.application.Platform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VisualiserApp extends Application {
    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        Label status = new Label("Ready.");
        status.setStyle("-fx-text-fill: #E6E6E6; -fx-padding: 6 8 8 8;");
        ControlsPane controls = new ControlsPane();

        // Make instance and result object
       Instance inst = InstanceCreator.createFDInstance().get(0);
        // Instance inst = InstanceCreator.contiguousPartitions().get(25); // 25 is een mooie visualisatie
        Result res = solveInstance(inst);

        // Make initial state and upcoming events
        SimState state = SimState.from(inst, res);
        List<ScheduledEvent> schedule = buildSchedule(inst, res);

        // Visualise
        int[] index = {0};
        boolean[] paused     = {false};
        boolean[] debugMode  = {false};
        long[] lastNs = {0L};
        double[] totalMinutes = {9 * 60.0};

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #111111;");
        root.setTop(controls);
        root.setBottom(status);
        GraphPane graph = new GraphPane(inst, state);
        StackPane center = new StackPane(graph);
        center.setAlignment(Pos.CENTER);
        root.setCenter(center);

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (paused[0]) {
                    lastNs[0] = now;
                    return;
                }
                if (lastNs[0] == 0L) {
                    lastNs[0] = now;
                    return;
                }
                double dt = (now - lastNs[0]) / 1_000_000_000.0;
                lastNs[0] = now;

                double speed = controls.speedSlider().getValue();
                totalMinutes[0] += speed * dt * 60.0;

                while (index[0] < schedule.size() && totalMinutes[0] >= schedule.get(index[0]).totalMinutes) {
                    Event e = schedule.get(index[0]++).event;
                    e.apply(state);
                    graph.refresh(state);
                    status.setText(e.label());
                }

                updateClock(controls, totalMinutes[0]);

                if (index[0] >= schedule.size()) {
                    status.setText("Done.");
                    paused[0] = true;
                    controls.setPaused(true);
                }
            }
        };
        timer.start();

        controls.setOnPause(a -> {
            paused[0] = !paused[0];
            controls.setPaused(paused[0]);
            lastNs[0] = 0L;
        });

        controls.setOnReset(a -> {
            state.resetFrom(inst, res);
            graph.refresh(state);
            index[0] = 0;
            totalMinutes[0] = 9 * 60.0;
            paused[0] = true;
            controls.setPaused(true);
            updateClock(controls, totalMinutes[0]);
            status.setText("Reset.");
        });

        controls.setOnDebug(a -> {
            debugMode[0] = !debugMode[0];
            controls.setDebugMode(debugMode[0]);
            graph.setDebugMode(debugMode[0]);
            graph.refresh(state);
            if (debugMode[0]) {
                // Auto-pause when entering debug mode
                paused[0] = true;
                controls.setPaused(true);
                lastNs[0] = 0L;
            }
        });

        controls.setOnNext(a -> {
            paused[0] = true;
            controls.setPaused(true);
            lastNs[0] = 0L;
            if (index[0] < schedule.size()) {
                ScheduledEvent se = schedule.get(index[0]++);
                se.event.apply(state);
                graph.refresh(state);
                status.setText(se.event.label());
                totalMinutes[0] = se.totalMinutes;
                updateClock(controls, totalMinutes[0]);
            }
        });

        controls.setOnPrev(a -> {
            if (index[0] > 0) {
                index[0]--;
                state.resetFrom(inst, res);
                for (int i = 0; i < index[0]; i++) schedule.get(i).event.apply(state);
                graph.refresh(state);
                totalMinutes[0] = index[0] > 0 ? schedule.get(index[0] - 1).totalMinutes : 9 * 60.0;
                updateClock(controls, totalMinutes[0]);
                status.setText(index[0] > 0 ? schedule.get(index[0] - 1).event.label() : "Reset.");
            }
        });

        stage.setScene(new Scene(root, 1000, 750));
        stage.setMaximized(true);
        stage.show();
        graph.applyCss();
        graph.layout();
        graph.refresh(state);
        center.widthProperty().addListener((obs, oldV, newV) -> {
            graph.rebuild(center.getWidth(), center.getHeight());
            graph.refresh(state);
        });
        center.heightProperty().addListener((obs, oldV, newV) -> {
            graph.rebuild(center.getWidth(), center.getHeight());
            graph.refresh(state);
        });
        stage.widthProperty().addListener((obs, oldV, newV) -> {
            graph.rebuild(center.getWidth(), center.getHeight());
            graph.refresh(state);
        });
        stage.heightProperty().addListener((obs, oldV, newV) -> {
            graph.rebuild(center.getWidth(), center.getHeight());
            graph.refresh(state);
        });
        stage.setOnShown(e -> Platform.runLater(() -> {
            graph.rebuild(center.getWidth(), center.getHeight());
            graph.refresh(state);
        }));

    }

    private static Result solveInstance(Instance inst) {
        List<Result> results = CapacitatedResupplyMILP.solveInstances(List.of(inst));
        if (results.isEmpty()) {
            throw new IllegalStateException("No result returned for instance.");
        }
        return results.get(0);
    }

    private static List<ScheduledEvent> buildSchedule(Instance inst, Result result) {
        List<ScheduledEvent> events = new ArrayList<>();
        Set<String> nodes = new HashSet<>();
        nodes.add("MSC");
        List<String> fscNames = new ArrayList<>();
        List<String> ouNames = new ArrayList<>();
        inst.FSCs.forEach(fsc -> {
            nodes.add(fsc.FSCname);
            fscNames.add(fsc.FSCname);
        });
        inst.operatingUnits.forEach(ou -> {
            nodes.add(ou.operatingUnitName);
            ouNames.add(ou.operatingUnitName);
        });

        for (int t = 1; t <= inst.timeHorizon; t++) {
            Map<String, DemandEvent.Demand> demands = new HashMap<>();
            for (OperatingUnit ou : inst.operatingUnits) {
                long dFW = Math.round(ou.dailyFoodWaterKg);
                long dFuel = Math.round(ou.dailyFuelKg);
                long dAmmo = Math.round(ou.dailyAmmoKg);
                demands.put(ou.operatingUnitName, new DemandEvent.Demand(dFW, dFuel, dAmmo));
            }
            events.add(new ScheduledEvent(t, 10 * 60, new DemandEvent(t, demands)));

            if (result == null) {
                continue;
            }

            // Phase 1 (14:00): FSC -> OU supply
            Map<String, Integer> fscArcTrucks = new HashMap<>();
            for (Map.Entry<Result.XKey, Integer> e : result.getXValue().entrySet()) {
                Result.XKey key = e.getKey();
                if (key.t() != t) continue;
                String arc = key.fsc() + "->" + key.ou();
                if (nodes.contains(key.fsc()) && nodes.contains(key.ou())) {
                    fscArcTrucks.merge(arc, e.getValue(), Integer::sum);
                }
            }
            events.add(new ScheduledEvent(t, 14 * 60, new FSCDeliveryEvent(t, fscArcTrucks, ouNames, fscNames, result)));

            // Phase 2 (20:00): MSC -> FSC and MSC -> VUST supply
            Map<String, Integer> mscArcTrucks = new HashMap<>();
            for (Map.Entry<Result.YKey, Integer> e : result.getYValue().entrySet()) {
                Result.YKey key = e.getKey();
                if (key.t() != t) continue;
                if (!nodes.contains(key.fsc())) continue;
                mscArcTrucks.merge("MSC->" + key.fsc(), e.getValue(), Integer::sum);
            }

            int mscToVust = 0;
            for (Map.Entry<Result.ZKey, Integer> e : result.getZValue().entrySet()) {
                Result.ZKey key = e.getKey();
                if (key.t() != t) continue;
                mscToVust += e.getValue();
            }
            if (mscToVust > 0 && nodes.contains("VUST")) {
                mscArcTrucks.merge("MSC->VUST", mscToVust, Integer::sum);
            }

            events.add(new ScheduledEvent(t, 20 * 60, new DeliveryEvent(t, mscArcTrucks, ouNames, fscNames, result)));
        }

        return events;
    }

    private static void updateClock(ControlsPane controls, double totalMinutes) {
        int day = (int) (totalMinutes / (24 * 60)) + 1;
        int minutesOfDay = (int) (totalMinutes % (24 * 60));
        int hours = minutesOfDay / 60;
        String text = String.format("Day %d %02d:00", day, hours);
        controls.setClockText(text);
    }

    private static class ScheduledEvent {
        final int   totalMinutes;
        final Event event;

        ScheduledEvent(int day, int minutesOfDay, Event event) {
            this.totalMinutes = (day - 1) * 24 * 60 + minutesOfDay;
            this.event = event;
        }
    }
}
