package Visualisation;

import Objects.Instance;
import Objects.OperatingUnit;
import Objects.Result;
import Visualisation.model.DeliveryEvent;
import Visualisation.model.DemandEvent;
import Visualisation.model.FSCDeliveryEvent;
import Visualisation.model.SimState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Headless exporter: runs the simulation and writes one JSON file per scenario,
 * plus a scenarios.json manifest so the GitHub Pages viewer can switch between them.
 *
 * Usage:
 *   SimExporter.export(inst1, res1, "docs/sim_fd.json",        "FD Concept");
 *   SimExporter.export(inst2, res2, "docs/sim_dispersed.json", "Dispersed Concept");
 *   SimExporter.writeManifest("docs/scenarios.json");
 *
 * @author 621349it Ies Timmerarends
 * @author 612348ih Isabel Hellebrekers
 * @author 631426ls Lena Stiebing
 * @author 661267eb Eeke Bavelaar
 */
public class SimExporter {

    /** Accumulated manifest entries â€” populated by each export() call. */
    private static final List<String> manifestEntries = new ArrayList<>();

    /**
     * Export one scenario and register it in the manifest.
     *
     * @param inst        the instance to simulate
     * @param res         the solved result
     * @param outputPath  path to write the JSON file, e.g. "docs/sim_fd.json"
     * @param displayName human-readable label shown in the viewer dropdown
     */
    public static void export(Instance inst, Result res, String outputPath, String displayName) throws IOException {
        List<String> ouNames  = new ArrayList<>();
        List<String> fscNames = new ArrayList<>();
        Set<String>  nodes    = new HashSet<>();
        nodes.add("MSC");
        for (var fsc : inst.getFSCs())            { nodes.add(fsc.getName());             fscNames.add(fsc.getName()); }
        for (var ou  : inst.getOperatingUnits())  { nodes.add(ou.getName());    ouNames.add(ou.getName()); }

        SimState state = SimState.from(inst, res);

        StringBuilder json = new StringBuilder();
        json.append("{\n");

        // â”€â”€ nodes â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        json.append("  \"nodes\": [\n");
        List<String> nodeParts = new ArrayList<>();
        nodeParts.add("    {\"name\": \"MSC\", \"type\": \"MSC\"}");
        for (var fsc : inst.getFSCs()) {
            nodeParts.add("    {\"name\": " + q(fsc.getName()) + ", \"type\": \"FSC\"}");
        }
        for (var ou : inst.getOperatingUnits()) {
            String type = ("VUST".equals(ou.getOuType()) || "VUST".equals(ou.getName())) ? "VUST" : "OU";
            nodeParts.add("    {\"name\": " + q(ou.getName()) + ", \"type\": " + q(type) + "}");
        }
        json.append(String.join(",\n", nodeParts)).append("\n  ],\n");

        // â”€â”€ edges â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        json.append("  \"edges\": [\n");
        List<String> edgeParts = new ArrayList<>();
        for (var fsc : inst.getFSCs()) {
            edgeParts.add("    {\"from\": \"MSC\", \"to\": " + q(fsc.getName()) + "}");
        }
        for (var ou : inst.getOperatingUnits()) {
            if (ou.getSource() != null && !ou.getSource().isBlank()) {
                edgeParts.add("    {\"from\": " + q(ou.getSource()) + ", \"to\": " + q(ou.getName()) + "}");
            }
        }
        json.append(String.join(",\n", edgeParts)).append("\n  ],\n");

        // â”€â”€ frames â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        json.append("  \"frames\": [\n");
        List<String> frames = new ArrayList<>();

        // Frame 0: initial state before any demand
        frames.add(snapshot(state, inst, "Initial state (full inventories)", "Day 1 09:00"));

        for (int t = 1; t <= inst.getTimeHorizon(); t++) {
            // 10:00 â€” demand
            Map<String, DemandEvent.Demand> demands = new HashMap<>();
            for (OperatingUnit ou : inst.getOperatingUnits()) {
                demands.put(ou.getName(), new DemandEvent.Demand(
                        ou.getDailyFoodWaterKg(), ou.getDailyFuelKg(), ou.getDailyAmmoKg()));
            }
            new DemandEvent(t, demands).apply(state);
            frames.add(snapshot(state, inst, "t=" + t + " Demand (all OUs)", "Day " + t + " 10:00"));

            if (res == null) { continue; }

            // 14:00 â€” FSC â†’ OU delivery
            Map<String, Integer> fscArcs = new HashMap<>();
            for (var e : res.getXValue().entrySet()) {
                var key = e.getKey();
                if (key.t() != t) { continue; }
                if (nodes.contains(key.fsc()) && nodes.contains(key.ou())) {
                    fscArcs.merge(key.fsc() + "->" + key.ou(), e.getValue(), Integer::sum);
                }
            }
            new FSCDeliveryEvent(t, fscArcs, ouNames, fscNames, res).apply(state);
            frames.add(snapshot(state, inst, "t=" + t + " FSC \u2192 OU supply", "Day " + t + " 14:00"));

            // 20:00 â€” MSC â†’ FSC / VUST delivery
            Map<String, Integer> mscArcs = new HashMap<>();
            for (var e : res.getYValue().entrySet()) {
                var key = e.getKey();
                if (key.t() != t || !nodes.contains(key.fsc())) { continue; }
                mscArcs.merge("MSC->" + key.fsc(), e.getValue(), Integer::sum);
            }
            int mscToVust = 0;
            for (var e : res.getZValue().entrySet()) {
                if (e.getKey().t() == t) { mscToVust += e.getValue(); }
            }
            if (mscToVust > 0 && nodes.contains("VUST")) {
                mscArcs.merge("MSC->VUST", mscToVust, Integer::sum);
            }
            new DeliveryEvent(t, mscArcs, ouNames, fscNames, res).apply(state);
            frames.add(snapshot(state, inst, "t=" + t + " MSC \u2192 FSC/VUST supply", "Day " + t + " 20:00"));
        }

        json.append(String.join(",\n", frames)).append("\n  ]\n}");

        Path out = Paths.get(outputPath);
        Files.createDirectories(out.getParent());
        Files.writeString(out, json.toString());
        System.out.println("[SimExporter] Written " + frames.size() + " frames â†’ " + out.toAbsolutePath());

        // Register in manifest (filename only, relative to docs/)
        manifestEntries.add("  {\"name\": " + q(displayName) + ", \"file\": " + q(out.getFileName().toString()) + "}");
    }

    /**
     * Write docs/scenarios.json listing every scenario exported so far.
     * Call this once after all export() calls.
     */
    public static void writeManifest(String manifestPath) throws IOException {
        String content = "[\n" + String.join(",\n", manifestEntries) + "\n]";
        Path out = Paths.get(manifestPath);
        Files.createDirectories(out.getParent());
        Files.writeString(out, content);
        System.out.println("[SimExporter] Manifest â†’ " + out.toAbsolutePath());
        manifestEntries.clear();   // reset for next run
    }

    // â”€â”€ helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private static String snapshot(SimState state, Instance inst, String label, String time) {
        StringBuilder sb = new StringBuilder();
        sb.append("    {\n");
        sb.append("      \"label\": ").append(q(label)).append(",\n");
        sb.append("      \"time\": ").append(q(time)).append(",\n");

        // arc trucks
        sb.append("      \"arcTrucks\": {");
        List<String> arcs = new ArrayList<>();
        for (var fsc : inst.getFSCs()) {
            arcs.add(q("MSC->" + fsc.getName()) + ": " + state.getArcTrucks("MSC", fsc.getName()));
            for (var ou : inst.getOperatingUnits()) {
                if (fsc.getName().equals(ou.getSource())) {
                    arcs.add(q(fsc.getName() + "->" + ou.getName()) + ": "
                            + state.getArcTrucks(fsc.getName(), ou.getName()));
                }
            }
        }
        for (var ou : inst.getOperatingUnits()) {
            if ("MSC".equals(ou.getSource())) {
                arcs.add(q("MSC->" + ou.getName()) + ": "
                        + state.getArcTrucks("MSC", ou.getName()));
            }
        }
        sb.append(String.join(", ", arcs)).append("},\n");

        // node states
        sb.append("      \"nodes\": {\n");
        List<String> ns = new ArrayList<>();
        for (var fsc : inst.getFSCs()) {
            ns.add("        " + q(fsc.getName()) + ": {\"ccl\": " + state.getInventoryFSC(fsc.getName())
                    + ", \"cclMax\": " + state.getInventoryFSCMax(fsc.getName()) + "}");
        }
        for (var ou : inst.getOperatingUnits()) {
            long fw     = Math.round(state.getInventoryOU(ou.getName(), "FW"));
            long fwMax  = Math.round(state.getInventoryOUMax(ou.getName(), "FW"));
            long fuel   = Math.round(state.getInventoryOU(ou.getName(), "FUEL"));
            long fuelMax= Math.round(state.getInventoryOUMax(ou.getName(), "FUEL"));
            long ammo   = Math.round(state.getInventoryOU(ou.getName(), "AMMO"));
            long ammoMax= Math.round(state.getInventoryOUMax(ou.getName(), "AMMO"));
            ns.add("        " + q(ou.getName()) + ": {\"fw\": " + fw + ", \"fwMax\": " + fwMax
                    + ", \"fuel\": " + fuel + ", \"fuelMax\": " + fuelMax
                    + ", \"ammo\": " + ammo + ", \"ammoMax\": " + ammoMax + "}");
        }
        sb.append(String.join(",\n", ns)).append("\n      }\n    }");
        return sb.toString();
    }

    private static String q(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
