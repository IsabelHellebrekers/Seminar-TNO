package Visualisation.util;

import javafx.geometry.Point2D;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LayoutEngine {
    public static Map<String, Point2D> simple3Column(double w, double h, List<String> fscs, List<String> ous) {
        Map<String, Point2D> pos = new HashMap<>();
        double padding = 30.0;
        double minOuSpacing = 70.0;

        pos.put("MSC", new Point2D(100, h / 2));

        for (int i = 0; i < fscs.size(); i++) {
            double y = (fscs.size() == 1) ? h / 2 : padding + (h - 2 * padding) * (i / (double) (fscs.size() - 1));
            pos.put(fscs.get(i), new Point2D(w * 0.4, y));
        }

        if (ous.size() == 1) {
            pos.put(ous.get(0), new Point2D(w * 0.75, h / 2));
        } else if ((ous.size() - 1) * minOuSpacing <= (h - 2 * padding)) {
            for (int i = 0; i < ous.size(); i++) {
                double y = padding + i * minOuSpacing;
                pos.put(ous.get(i), new Point2D(w * 0.75, y));
            }
        } else {
            for (int i = 0; i < ous.size(); i++) {
                double y = padding + (h - 2 * padding) * (i / (double) (ous.size() - 1));
                pos.put(ous.get(i), new Point2D(w * 0.75, y));
            }
        }

        return pos;
    }

    public static Map<String, Point2D> simple3Column(double w, double h, List<String> fscs, List<String> vusts, List<String> ous) {
        Map<String, Point2D> pos = new HashMap<>();
        double padding = 30.0;
        double minOuSpacing = 70.0;

        pos.put("MSC", new Point2D(100, h / 2));

        List<String> middleNodes = new java.util.ArrayList<>(fscs);
        middleNodes.addAll(vusts);

        for (int i = 0; i < middleNodes.size(); i++) {
            double y = (middleNodes.size() == 1)
                    ? h / 2
                    : padding + (h - 2 * padding) * (i / (double) (middleNodes.size() - 1));
            pos.put(middleNodes.get(i), new Point2D(w * 0.4, y));
        }

        if (ous.size() == 1) {
            pos.put(ous.get(0), new Point2D(w * 0.75, h / 2));
        } else if ((ous.size() - 1) * minOuSpacing <= (h - 2 * padding)) {
            for (int i = 0; i < ous.size(); i++) {
                double y = padding + i * minOuSpacing;
                pos.put(ous.get(i), new Point2D(w * 0.75, y));
            }
        } else {
            for (int i = 0; i < ous.size(); i++) {
                double y = padding + (h - 2 * padding) * (i / (double) (ous.size() - 1));
                pos.put(ous.get(i), new Point2D(w * 0.75, y));
            }
        }

        return pos;
    }
}
