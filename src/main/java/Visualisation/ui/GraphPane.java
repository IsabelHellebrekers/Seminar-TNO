package Visualisation.ui;

import Objects.Instance;
import Objects.OperatingUnit;
import Visualisation.model.SimState;
import Visualisation.util.LayoutEngine;
import javafx.scene.control.Label;
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

/**
 * JavaFX pane that renders the supply network graph.
 * Displays nodes (MSC, FSCs, OUs) with inventory bars and directed arcs
 * whose colour intensity reflects the number of trucks in transit.
 *
 * @author 621349it Ies Timmerarends
 * @author 612348ih Isabel Hellebrekers
 * @author 631426ls Lena Stiebing
 * @author 661267eb Eeke Bavelaar
 */
public class GraphPane extends Pane {
    private static final double NODE_WIDTH      = 155.0;
    private static final double NODE_HEIGHT     = 50.0;
    private static final double ARROW_SIZE      = 8.0;
    private static final double ARROW_ANGLE_DEG = 25.0;
    private static final Color  EDGE_BASE       = Color.web("#2C2C2C");

    // Styles for arc truck-count labels
    private static final String ARC_ACTIVE =
            "-fx-text-fill: #00FF88; -fx-font-size: 11px; -fx-font-family: 'Consolas'; -fx-font-weight: bold;" +
            "-fx-background-color: #0A1A0F; -fx-padding: 1 4 1 4;" +
            "-fx-border-color: #00FF88; -fx-border-width: 1; -fx-border-radius: 3; -fx-background-radius: 3;";
    private static final String ARC_INACTIVE =
            "-fx-text-fill: #444444; -fx-font-size: 11px; -fx-font-family: 'Consolas';" +
            "-fx-background-color: #0D0D0D; -fx-padding: 1 4 1 4;" +
            "-fx-border-color: #2A2A2A; -fx-border-width: 1; -fx-border-radius: 3; -fx-background-radius: 3;";

    private final Instance inst;
    private final Map<String, Point2D>           pos      = new HashMap<>();
    private final Map<String, Edge>              edges    = new HashMap<>();
    private final Map<String, OperatingUnitPane> ouNodes  = new HashMap<>();
    private final Map<String, FscPane>           fscNodes = new HashMap<>();

    private boolean debugMode = false;

    /**
     * Construct a GraphPane for the given instance with no initial state.
     *
     * @param inst the problem instance defining the network topology
     */
    public GraphPane(Instance inst) {
        setPrefSize(900, 700);
        setStyle("-fx-background-color: #151515;");
        this.inst = inst;
        rebuild(getPrefWidth(), getPrefHeight());
    }

    /**
     * Construct a GraphPane and immediately apply the given simulation state.
     *
     * @param inst  the problem instance defining the network topology
     * @param state the initial simulation state to display, or null for defaults
     */
    public GraphPane(Instance inst, SimState state) {
        this(inst);
        if (state != null) {
            refresh(state);
        }
    }

    /** Toggle debug overlays on arcs and inventory nodes. */
    public void setDebugMode(boolean debug) {
        this.debugMode = debug;
        for (FscPane p : fscNodes.values()) {
            p.setDebugMode(debug);
        }
        for (OperatingUnitPane p : ouNodes.values()) {
            p.setDebugMode(debug);
        }
    }

    /**
     * Rebuild all visual elements at a new size.
     * Clears and recreates nodes, edges, and labels to fit the given dimensions.
     *
     * @param width  the new pane width in pixels
     * @param height the new pane height in pixels
     */
    public void rebuild(double width, double height) {
        if (width <= 0 || height <= 0) { return; }
        setPrefSize(width, height);
        getChildren().clear();
        edges.clear();
        ouNodes.clear();
        fscNodes.clear();
        pos.clear();

        List<String> fscNames  = new ArrayList<>();
        List<String> ouNames   = new ArrayList<>();
        List<String> vustNames = new ArrayList<>();

        inst.getFSCs().forEach(fsc -> fscNames.add(fsc.getName()));
        inst.getOperatingUnits().forEach(ou -> {
            if ("VUST".equals(ou.getOuType()) || "VUST".equals(ou.getName())) {
                vustNames.add(ou.getName());
            } else {
                ouNames.add(ou.getName());
            }
        });

        this.pos.putAll(LayoutEngine.simple3Column(width, height, fscNames, vustNames, ouNames));

        drawMscNode();
        for (String fscName : fscNames) {
            drawFscNode(fscName);
            drawEdge("MSC", fscName);
        }
        for (OperatingUnit ou : inst.getOperatingUnits()) {
            String ouName = ou.getName();
            drawOuNode(ouName);
            if (ou.getSource() != null && !ou.getSource().isBlank()) {
                drawEdge(ou.getSource(), ouName);
            }
        }

        // Re-apply debug mode to freshly created node panes after rebuild
        if (this.debugMode) {
            for (FscPane p : fscNodes.values()) {
                p.setDebugMode(true);
            }
            for (OperatingUnitPane p : ouNodes.values()) {
                p.setDebugMode(true);
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
        if (p == null) { return; }
        FscPane node = new FscPane(name);
        placeNode(node, p);
        getChildren().add(node);
        this.fscNodes.put(name, node);
    }

    private void drawOuNode(String name) {
        Point2D p = this.pos.get(name);
        if (p == null) { return; }
        OperatingUnitPane node = new OperatingUnitPane(name);
        placeNode(node, p);
        getChildren().add(node);
        this.ouNodes.put(name, node);
    }

    private void placeNode(Pane node, Point2D p) {
        node.setPrefSize(NODE_WIDTH, NODE_HEIGHT);
        node.setLayoutX(p.getX() - NODE_WIDTH  / 2);
        node.setLayoutY(p.getY() - NODE_HEIGHT / 2);
    }

    private void drawEdge(String from, String to) {
        Point2D a = this.pos.get(from);
        Point2D b = this.pos.get(to);
        if (a == null || b == null) { return; }

        Point2D start = rightMiddle(a);
        Point2D end   = leftMiddle(b);

        Line l = new Line(start.getX(), start.getY(), end.getX(), end.getY());
        l.setStrokeWidth(2);
        l.setStroke(EDGE_BASE);

        Polygon arrow = makeArrowHead(start, end);
        arrow.setFill(EDGE_BASE);

        // Truck-count label: placed at arc midpoint, offset perpendicular to the arc
        double mx  = (start.getX() + end.getX()) / 2.0;
        double my  = (start.getY() + end.getY()) / 2.0;
        double dx  = end.getX() - start.getX();
        double dy  = end.getY() - start.getY();
        double len = Math.sqrt(dx * dx + dy * dy);
        double nx  = (len > 0) ? -dy / len : 0.0;  // unit perpendicular (screen: +y is down)
        double ny  = (len > 0) ?  dx / len : 0.0;

        Label truckLabel = new Label("0");
        truckLabel.setStyle(ARC_INACTIVE);
        truckLabel.setVisible(false);
        truckLabel.setLayoutX(mx + nx * 14 - 18); // -18: approximate half-width centering
        truckLabel.setLayoutY(my + ny * 14 -  9); //  -9: approximate half-height centering

        // Lines/arrows go to the back; label stays on top
        getChildren().addAll(l, arrow);
        l.toBack();
        arrow.toBack();
        getChildren().add(truckLabel);

        this.edges.put(from + "->" + to, new Edge(l, arrow, truckLabel));
    }

    /**
     * Refresh all node and arc visuals to reflect the current simulation state.
     *
     * @param state the current simulation state
     */
    public void refresh(SimState state) {
        int maxTrucks = 0;
        for (var e : this.edges.entrySet()) {
            String[] parts = e.getKey().split("->");
            int t = state.getArcTrucks(parts[0], parts[1]);
            if (t > maxTrucks) {
                maxTrucks = t;
            }
        }

        for (var e : this.edges.entrySet()) {
            String   key    = e.getKey();
            Edge     edge   = e.getValue();
            Line     line   = edge.line;
            Polygon  arrow  = edge.arrow;
            String[] parts  = key.split("->");
            int      trucks = state.getArcTrucks(parts[0], parts[1]);

            if (trucks <= 0) {
                line.setStroke(EDGE_BASE);
                arrow.setFill(EDGE_BASE);
                line.setOpacity(0.25);
                arrow.setOpacity(0.25);
                line.setStrokeWidth(2);
                edge.glow.setRadius(0.5);
                edge.glow.setSpread(0.0);
                edge.glow.setColor(Color.color(0.2, 0.2, 0.2, 0.02));
            } else {
                double norm      = (maxTrucks <= 0) ? 0.0 : (double) trucks / maxTrucks;
                double intensity = Math.pow(norm, 0.18);
                Color  mixed     = Color.web("#3A3A3A").interpolate(Color.web("#FF2A2A"), intensity);
                line.setStroke(mixed);
                arrow.setFill(mixed);
                line.setOpacity(0.25 + 0.75 * intensity);
                arrow.setOpacity(0.25 + 0.75 * intensity);
                line.setStrokeWidth(2);
                edge.glow.setRadius(1 + 40 * intensity);
                edge.glow.setSpread(0.65 * intensity);
                edge.glow.setColor(Color.color(1.0, 0.2, 0.2, 0.05 + 0.95 * intensity));
            }

            // Arc truck label â€” only visible in debug mode
            if (this.debugMode) {
                edge.truckLabel.setVisible(true);
                if (trucks > 0) {
                    edge.truckLabel.setText(String.valueOf(trucks));
                    edge.truckLabel.setStyle(ARC_ACTIVE);
                } else {
                    edge.truckLabel.setText("0");
                    edge.truckLabel.setStyle(ARC_INACTIVE);
                }
            } else {
                edge.truckLabel.setVisible(false);
            }
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
        double dx    = end.getX() - start.getX();
        double dy    = end.getY() - start.getY();
        double angle = Math.atan2(dy, dx);
        double theta = Math.toRadians(ARROW_ANGLE_DEG);
        double x  = end.getX();
        double y  = end.getY();
        double x1 = x - ARROW_SIZE * Math.cos(angle - theta);
        double y1 = y - ARROW_SIZE * Math.sin(angle - theta);
        double x2 = x - ARROW_SIZE * Math.cos(angle + theta);
        double y2 = y - ARROW_SIZE * Math.sin(angle + theta);
        return new Polygon(x, y, x1, y1, x2, y2);
    }

    private static class Edge {
        final Line       line;
        final Polygon    arrow;
        final DropShadow glow;
        final Label      truckLabel;

        Edge(Line line, Polygon arrow, Label truckLabel) {
            this.line       = line;
            this.arrow      = arrow;
            this.truckLabel = truckLabel;
            this.glow       = new DropShadow(1, Color.color(0.3, 0.3, 0.3, 0.1));
            this.line.setEffect(this.glow);
            this.arrow.setEffect(this.glow);
        }
    }
}
