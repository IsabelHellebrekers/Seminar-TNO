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
 */
public class SimExporter {

    /** Accumulated manifest entries — populated by each export() call. */
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
        for (var fsc : inst.FSCs)            { nodes.add(fsc.FSCname);             fscNames.add(fsc.FSCname); }
        for (var ou  : inst.operatingUnits)  { nodes.add(ou.operatingUnitName);    ouNames.add(ou.operatingUnitName); }

        SimState state = SimState.from(inst, res);

        StringBuilder json = new StringBuilder();
        json.append("{\n");

        // ── nodes ──────────────────────────────────────────────────────────────
        json.append("  \"nodes\": [\n");
        List<String> nodeParts = new ArrayList<>();
        nodeParts.add("    {\"name\": \"MSC\", \"type\": \"MSC\"}");
        for (var fsc : inst.FSCs)
            nodeParts.add("    {\"name\": " + q(fsc.FSCname) + ", \"type\": \"FSC\"}");
        for (var ou : inst.operatingUnits) {
            String type = ("VUST".equals(ou.ouType) || "VUST".equals(ou.operatingUnitName)) ? "VUST" : "OU";
            nodeParts.add("    {\"name\": " + q(ou.operatingUnitName) + ", \"type\": " + q(type) + "}");
        }
        json.append(String.join(",\n", nodeParts)).append("\n  ],\n");

        // ── edges ──────────────────────────────────────────────────────────────
        json.append("  \"edges\": [\n");
        List<String> edgeParts = new ArrayList<>();
        for (var fsc : inst.FSCs)
            edgeParts.add("    {\"from\": \"MSC\", \"to\": " + q(fsc.FSCname) + "}");
        for (var ou : inst.operatingUnits)
            if (ou.source != null && !ou.source.isBlank())
                edgeParts.add("    {\"from\": " + q(ou.source) + ", \"to\": " + q(ou.operatingUnitName) + "}");
        json.append(String.join(",\n", edgeParts)).append("\n  ],\n");

        // ── frames ─────────────────────────────────────────────────────────────
        json.append("  \"frames\": [\n");
        List<String> frames = new ArrayList<>();

        // Frame 0: initial state before any demand
        frames.add(snapshot(state, inst, "Initial state (full inventories)", "Day 1 09:00"));

        for (int t = 1; t <= inst.timeHorizon; t++) {
            // 10:00 — demand
            Map<String, DemandEvent.Demand> demands = new HashMap<>();
            for (OperatingUnit ou : inst.operatingUnits)
                demands.put(ou.operatingUnitName, new DemandEvent.Demand(
                        ou.dailyFoodWaterKg, ou.dailyFuelKg, ou.dailyAmmoKg));
            new DemandEvent(t, demands).apply(state);
            frames.add(snapshot(state, inst, "t=" + t + " Demand (all OUs)", "Day " + t + " 10:00"));

            if (res == null) continue;

            // 14:00 — FSC → OU delivery
            Map<String, Integer> fscArcs = new HashMap<>();
            for (var e : res.getXValue().entrySet()) {
                var key = e.getKey();
                if (key.t() != t) continue;
                if (nodes.contains(key.fsc()) && nodes.contains(key.ou()))
                    fscArcs.merge(key.fsc() + "->" + key.ou(), e.getValue(), Integer::sum);
            }
            new FSCDeliveryEvent(t, fscArcs, ouNames, fscNames, res).apply(state);
            frames.add(snapshot(state, inst, "t=" + t + " FSC \u2192 OU supply", "Day " + t + " 14:00"));

            // 20:00 — MSC → FSC / VUST delivery
            Map<String, Integer> mscArcs = new HashMap<>();
            for (var e : res.getYValue().entrySet()) {
                var key = e.getKey();
                if (key.t() != t || !nodes.contains(key.fsc())) continue;
                mscArcs.merge("MSC->" + key.fsc(), e.getValue(), Integer::sum);
            }
            int mscToVust = 0;
            for (var e : res.getZValue().entrySet())
                if (e.getKey().t() == t) mscToVust += e.getValue();
            if (mscToVust > 0 && nodes.contains("VUST"))
                mscArcs.merge("MSC->VUST", mscToVust, Integer::sum);
            new DeliveryEvent(t, mscArcs, ouNames, fscNames, res).apply(state);
            frames.add(snapshot(state, inst, "t=" + t + " MSC \u2192 FSC/VUST supply", "Day " + t + " 20:00"));
        }

        json.append(String.join(",\n", frames)).append("\n  ]\n}");

        Path out = Paths.get(outputPath);
        Files.createDirectories(out.getParent());
        Files.writeString(out, json.toString());
        System.out.println("[SimExporter] Written " + frames.size() + " frames → " + out.toAbsolutePath());

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
        System.out.println("[SimExporter] Manifest → " + out.toAbsolutePath());
        manifestEntries.clear();   // reset for next run
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static String snapshot(SimState state, Instance inst, String label, String time) {
        StringBuilder sb = new StringBuilder();
        sb.append("    {\n");
        sb.append("      \"label\": ").append(q(label)).append(",\n");
        sb.append("      \"time\": ").append(q(time)).append(",\n");

        // arc trucks
        sb.append("      \"arcTrucks\": {");
        List<String> arcs = new ArrayList<>();
        for (var fsc : inst.FSCs) {
            arcs.add(q("MSC->" + fsc.FSCname) + ": " + state.getArcTrucks("MSC", fsc.FSCname));
            for (var ou : inst.operatingUnits)
                if (fsc.FSCname.equals(ou.source))
                    arcs.add(q(fsc.FSCname + "->" + ou.operatingUnitName) + ": "
                            + state.getArcTrucks(fsc.FSCname, ou.operatingUnitName));
        }
        for (var ou : inst.operatingUnits)
            if ("MSC".equals(ou.source))
                arcs.add(q("MSC->" + ou.operatingUnitName) + ": "
                        + state.getArcTrucks("MSC", ou.operatingUnitName));
        sb.append(String.join(", ", arcs)).append("},\n");

        // node states
        sb.append("      \"nodes\": {\n");
        List<String> ns = new ArrayList<>();
        for (var fsc : inst.FSCs)
            ns.add("        " + q(fsc.FSCname) + ": {\"ccl\": " + state.getInventoryFSC(fsc.FSCname)
                    + ", \"cclMax\": " + state.getInventoryFSCMax(fsc.FSCname) + "}");
        for (var ou : inst.operatingUnits) {
            long fw     = Math.round(state.getInventoryOU(ou.operatingUnitName, "FW"));
            long fwMax  = Math.round(state.getInventoryOUMax(ou.operatingUnitName, "FW"));
            long fuel   = Math.round(state.getInventoryOU(ou.operatingUnitName, "FUEL"));
            long fuelMax= Math.round(state.getInventoryOUMax(ou.operatingUnitName, "FUEL"));
            long ammo   = Math.round(state.getInventoryOU(ou.operatingUnitName, "AMMO"));
            long ammoMax= Math.round(state.getInventoryOUMax(ou.operatingUnitName, "AMMO"));
            ns.add("        " + q(ou.operatingUnitName) + ": {\"fw\": " + fw + ", \"fwMax\": " + fwMax
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
