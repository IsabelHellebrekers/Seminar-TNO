package Visualisation.util;

import javafx.geometry.Point2D;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LayoutEngine {
    public static Map<String, Point2D> simple3Column(double w, double h, List<String> fscs, List<String> ous) {
        Map<String, Point2D> pos = new HashMap<>();
        double padding = Math.max(40.0, h * 0.04);

        pos.put("MSC", new Point2D(Math.max(80, w * 0.06), h / 2));

        double availH = h - 2 * padding;
        placeNodes(pos, fscs, w * 0.4,  padding, availH);
        placeNodes(pos, ous,  w * 0.88, padding, availH);

        return pos;
    }

    public static Map<String, Point2D> simple3Column(double w, double h, List<String> fscs, List<String> vusts, List<String> ous) {
        Map<String, Point2D> pos = new HashMap<>();
        // Use at least 40 px padding (or 4% of height on tall screens) so nodes never bleed off-screen
        double padding = Math.max(40.0, h * 0.04);

        // MSC: relative to width, vertically centered
        pos.put("MSC", new Point2D(Math.max(80, w * 0.06), h / 2));

        // Middle column: FSCs first, then VUSTs — all evenly spread over the full height
        List<String> middleNodes = new java.util.ArrayList<>(fscs);
        middleNodes.addAll(vusts);
        placeNodes(pos, middleNodes, w * 0.4, padding, h - 2 * padding);

        // OUs: right column, span full height
        placeNodes(pos, ous, w * 0.88, padding, h - 2 * padding);

        return pos;
    }

    /** Places {@code nodes} evenly within the vertical range [startY, startY+rangeH]. */
    private static void placeNodes(Map<String, Point2D> pos, List<String> nodes,
                                   double x, double startY, double rangeH) {
        if (nodes.isEmpty()) { return; }
        if (nodes.size() == 1) {
            pos.put(nodes.get(0), new Point2D(x, startY + rangeH / 2.0));
            return;
        }
        for (int i = 0; i < nodes.size(); i++) {
            double y = startY + rangeH * i / (double) (nodes.size() - 1);
            pos.put(nodes.get(i), new Point2D(x, y));
        }
    }
}
