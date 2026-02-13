package Visualisation.ui;

import Objects.Instance;
import Objects.OperatingUnit;
import Visualisation.model.SimState;
import Visualisation.util.LayoutEngine;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;

import javafx.geometry.Point2D;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GraphPane extends Pane {
    private static final double NODE_WIDTH = 110.0;
    private static final double NODE_HEIGHT = 50.0;
    private static final double ARROW_SIZE = 8.0;
    private static final double ARROW_ANGLE_DEG = 25.0;
    private static final Color EDGE_BASE = Color.web("#2C2C2C");

    private final Instance inst;
    private final Map<String, Point2D> pos;
    private final Map<String, Edge> edges = new HashMap<>();
    private final Map<String, OperatingUnitPane> ouNodes = new HashMap<>();
    private final Map<String, FscPane> fscNodes = new HashMap<>();

    public GraphPane(Instance inst) {
        setPrefSize(900, 700);
        setStyle("-fx-background-color: #151515;");
        this.inst = inst;
        this.pos = new HashMap<>();
        rebuild(getPrefWidth(), getPrefHeight());
    }

    public GraphPane(Instance inst, SimState state) {
        this(inst);
        if (state != null) {
            refresh(state);
        }
    }

    public void rebuild(double width, double height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        setPrefSize(width, height);
        getChildren().clear();
        edges.clear();
        ouNodes.clear();
        fscNodes.clear();
        pos.clear();

        // Make FSC, VUST and OU names list
        List<String> fscNames = new ArrayList<>();
        inst.FSCs.forEach(fsc -> fscNames.add(fsc.FSCname));
        List<String> ouNames = new ArrayList<>();
        List<String> vustNames = new ArrayList<>();

        inst.operatingUnits.forEach(ou -> {
            if ("VUST".equals(ou.ouType) || "VUST".equals(ou.operatingUnitName)) {
                vustNames.add(ou.operatingUnitName);
            } else {
                ouNames.add(ou.operatingUnitName);
            }
        });

        // Make all points on the graph
        this.pos.putAll(LayoutEngine.simple3Column(width, height, fscNames, vustNames, ouNames));

        // Draw MSC
        drawMscNode();

        // Draw FSCs + edges
        for (String fscName : fscNames) {
            drawFscNode(fscName);
            drawEdge("MSC", fscName);
        }

        // Draw OUs + edges
        for (OperatingUnit ou : inst.operatingUnits) {
            String ouName = ou.operatingUnitName;
            drawOuNode(ouName);

            if (ou.source != null && !ou.source.isBlank()) {
                drawEdge(ou.source, ouName);
            }
        }
    }

    private void drawMscNode() {
        Point2D p = this.pos.get("MSC");
        MscPane node = new MscPane("MSC");
        placeNode(node, p);
        getChildren().add(node);
    }

    private void drawFscNode(String name) {
        Point2D p = this.pos.get(name);
        if (p == null) {
            return;
        }
        FscPane node = new FscPane(name);
        placeNode(node, p);
        getChildren().add(node);
        this.fscNodes.put(name, node);
    }

    private void drawOuNode(String name) {
        Point2D p = this.pos.get(name);
        if (p == null) {
            return;
        }
        OperatingUnitPane node = new OperatingUnitPane(name);
        placeNode(node, p);
        getChildren().add(node);
        this.ouNodes.put(name, node);
    }

    private void placeNode(Pane node, Point2D p) {
        node.setPrefSize(NODE_WIDTH, NODE_HEIGHT);
        node.setLayoutX(p.getX() - NODE_WIDTH / 2);
        node.setLayoutY(p.getY() - NODE_HEIGHT / 2);
    }

    private void drawEdge(String from, String to) {
        Point2D a = this.pos.get(from);
        Point2D b = this.pos.get(to);
        if (a == null || b == null) {
            return;
        }

        Point2D start = rightMiddle(a);
        Point2D end = leftMiddle(b);

        Line l = new Line(start.getX(), start.getY(), end.getX(), end.getY());
        l.setStrokeWidth(2);
        l.setStroke(EDGE_BASE);

        Polygon arrow = makeArrowHead(start, end);
        arrow.setFill(EDGE_BASE);

        getChildren().addAll(l, arrow);
        l.toBack();
        arrow.toBack();
        this.edges.put(from + "->" + to, new Edge(l, arrow));
    }

    public void refresh(SimState state) {
        int maxTrucks = 0;
        for (var e : this.edges.entrySet()) {
            String key = e.getKey();
            String[] parts = key.split("->");
            int trucks = state.getArcTrucks(parts[0], parts[1]);
            if (trucks > maxTrucks) maxTrucks = trucks;
        }

        for (var e : this.edges.entrySet()) {
            String key = e.getKey();
            Edge edge = e.getValue();
            Line line = edge.line;
            Polygon arrow = edge.arrow;

            String[] parts = key.split("->");
            int trucks = state.getArcTrucks(parts[0], parts[1]);

            if (trucks <= 0) {
                line.setStroke(EDGE_BASE);
                arrow.setFill(EDGE_BASE);
                line.setOpacity(0.25);
                arrow.setOpacity(0.25);
                line.setStrokeWidth(2);
                edge.glow.setRadius(0.5);
                edge.glow.setSpread(0.0);
                edge.glow.setColor(Color.color(0.2, 0.2, 0.2, 0.02));
                continue;
            }

            double norm = (maxTrucks <= 0) ? 0.0 : (double) trucks / (double) maxTrucks;
            double intensity = Math.pow(norm, 0.18);
            Color base = Color.web("#3A3A3A");
            Color hot = Color.web("#FF2A2A");
            Color mixed = base.interpolate(hot, intensity);

            line.setStroke(mixed);
            arrow.setFill(mixed);
            line.setOpacity(0.25 + 0.75 * intensity);
            arrow.setOpacity(0.25 + 0.75 * intensity);
            line.setStrokeWidth(2);

            edge.glow.setRadius(1 + 40 * intensity);
            edge.glow.setSpread(0.0 + 0.65 * intensity);
            edge.glow.setColor(Color.color(1.0, 0.2, 0.2, 0.05 + 0.95 * intensity));
        }

        for (OperatingUnitPane node : this.ouNodes.values()) {
            node.refresh(state);
        }

        for (FscPane node : this.fscNodes.values()) {
            node.refresh(state);
        }
    }

    private Point2D rightMiddle(Point2D center) {
        return new Point2D(center.getX() + NODE_WIDTH / 2.0, center.getY());
    }

    private Point2D leftMiddle(Point2D center) {
        return new Point2D(center.getX() - NODE_WIDTH / 2.0, center.getY());
    }

    private Polygon makeArrowHead(Point2D start, Point2D end) {
        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        double angle = Math.atan2(dy, dx);
        double theta = Math.toRadians(ARROW_ANGLE_DEG);

        double x = end.getX();
        double y = end.getY();

        double x1 = x - ARROW_SIZE * Math.cos(angle - theta);
        double y1 = y - ARROW_SIZE * Math.sin(angle - theta);
        double x2 = x - ARROW_SIZE * Math.cos(angle + theta);
        double y2 = y - ARROW_SIZE * Math.sin(angle + theta);

        return new Polygon(x, y, x1, y1, x2, y2);
    }

    private static class Edge {
        private final Line line;
        private final Polygon arrow;
        private final DropShadow glow;

        private Edge(Line line, Polygon arrow) {
            this.line = line;
            this.arrow = arrow;
            this.glow = new DropShadow(1, Color.color(0.3, 0.3, 0.3, 0.1));
            this.line.setEffect(this.glow);
            this.arrow.setEffect(this.glow);
        }
    }
}
