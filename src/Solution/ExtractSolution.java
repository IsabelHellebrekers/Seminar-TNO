package Solution;

import Objects.*;
import com.gurobi.gurobi.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class ExtractSolution {

    private static final String FSC1 = "FSC 1";
    private static final String FSC2 = "FSC 2";
    private static final String MSC = "MSC";
    private static final String VUST_NAME = "Vust";

    private ExtractSolution() {}

    /**
     * Extract an immutable ResupplySolution object from an optimized model.
     *
     * @param model solved Gurobi model
     * @param inst  instance data (used for OU lists, OU types, CCL types, capacities, etc.)
     * @param H     planning horizon in days
     */
    public static ResupplySolution extract(GRBModel model, Instance inst, int H) throws GRBException {
        int status = model.get(GRB.IntAttr.Status);
        if (status != GRB.Status.OPTIMAL && status != GRB.Status.SUBOPTIMAL) {
            throw new IllegalStateException("Model status is not optimal/suboptimal. Status=" + status);
        }

        List<String> fscs = List.of(FSC1, FSC2);

        List<String> cclTypes = new ArrayList<>(inst.cclContents.keySet());
        cclTypes.sort(Comparator.naturalOrder());

        List<OperatingUnit> ous = inst.operatingUnits;

        String vustOuName = null;
        for (OperatingUnit ou : ous) {
            if (VUST_NAME.equals(ou.operatingUnit)) {
                vustOuName = ou.operatingUnit;
                break;
            }
        }
        if (vustOuName == null) {
            throw new IllegalStateException("Could not find Vust OU with name '" + VUST_NAME + "' in operating units.");
        }

        // Objective & stationed trucks
        double obj = model.get(GRB.DoubleAttr.ObjVal);
        int trucksMSC = roundInt(getX(model, "M"));

        Map<String, Integer> trucksFSC = new LinkedHashMap<>();
        trucksFSC.put(FSC1, roundInt(getX(model, "K_FSC1")));
        trucksFSC.put(FSC2, roundInt(getX(model, "K_FSC2")));

        // Daily departures
        Map<Integer, Integer> dailyMSC = new LinkedHashMap<>();
        Map<String, Map<Integer, Integer>> dailyFSC = new LinkedHashMap<>();
        dailyFSC.put(FSC1, new LinkedHashMap<>());
        dailyFSC.put(FSC2, new LinkedHashMap<>());

        // Inventories
        Map<String, Map<Integer, Map<String, Double>>> ouInventory = new LinkedHashMap<>();
        Map<String, Map<Integer, Double>> fscInventoryTotal = new LinkedHashMap<>();
        fscInventoryTotal.put(FSC1, new LinkedHashMap<>());
        fscInventoryTotal.put(FSC2, new LinkedHashMap<>());

        Map<String, Map<Integer, Map<String, Map<String, Integer>>>> fscInventoryByBucket = new LinkedHashMap<>();
        for (String w : fscs) {
            fscInventoryByBucket.put(w, new LinkedHashMap<>());
        }

        // Positive shipments
        List<Shipment> shipments = new ArrayList<>();

        // Precompute OU lists served by each FSC based on ou.source
        Map<String, List<OperatingUnit>> ousByFsc = new HashMap<>();
        ousByFsc.put(FSC1, new ArrayList<>());
        ousByFsc.put(FSC2, new ArrayList<>());
        for (OperatingUnit ou : ous) {
            if (FSC1.equals(ou.source)) ousByFsc.get(FSC1).add(ou);
            if (FSC2.equals(ou.source)) ousByFsc.get(FSC2).add(ou);
        }

        // (1) Daily departures + y/z list
        for (int t = 1; t <= H; t++) {
            int usedMSC = 0;

            // y[w,c,o,t]
            for (String w : fscs) {
                for (String c : cclTypes) {
                    for (OuType o : OuType.values()) {
                        String varName = nameY(w, c, o, t);
                        int q = roundInt(getX(model, varName));
                        if (q > 0) {
                            usedMSC += q;
                            shipments.add(new Shipment(MSC, w, c, t, q, o.toString()));
                        }
                    }
                }
            }

            // z[c,t]
            for (String c : cclTypes) {
                String varName = nameZ(c, t);
                int q = roundInt(getX(model, varName));
                if (q > 0) {
                    usedMSC += q;
                    shipments.add(new Shipment(MSC, vustOuName, c, t, q, "VUST"));
                }
            }

            dailyMSC.put(t, usedMSC);
        }

        // (2) Daily FSC departures + x list
        for (String w : fscs) {
            for (int t = 1; t <= H; t++) {
                int used = 0;

                for (OperatingUnit ou : ousByFsc.getOrDefault(w, List.of())) {
                    String i = ou.operatingUnit;
                    for (String c : cclTypes) {
                        String varName = nameX(w, i, c, t);
                        GRBVar v = model.getVarByName(varName);
                        if (v == null) continue;

                        int q = roundInt(v.get(GRB.DoubleAttr.X));
                        if (q > 0) {
                            used += q;
                            shipments.add(new Shipment(w, i, c, t, q, ou.ouType.toString()));
                        }
                    }
                }

                dailyFSC.get(w).put(t, used);
            }
        }

        // (3) OU inventories I[i,p,t]
        for (OperatingUnit ou : ous) {
            String i = ou.operatingUnit;
            String iKey = i; 

            Map<Integer, Map<String, Double>> perDay = new LinkedHashMap<>();
            for (int t = 1; t <= H; t++) {
                Map<String, Double> prod = new LinkedHashMap<>();

                double fw = getX(model, nameI(i, "FW", t));
                double fuel = getX(model, nameI(i, "FUEL", t));
                double ammo = getX(model, nameI(i, "AMMO", t));

                prod.put("FW", fw);
                prod.put("FUEL", fuel);
                prod.put("AMMO", ammo);

                perDay.put(t, prod);
            }
            ouInventory.put(iKey, perDay);
        }

        // (4) FSC inventories 
        for (String w : fscs) {
            Map<Integer, Double> perDay = fscInventoryTotal.get(w);

            for (int t = 1; t <= H; t++) {
                double total = 0.0;

                for (String c : cclTypes) {
                    for (OuType o : OuType.values()) {
                        String varName = nameS(w, c, o, t);
                        total += getX(model, varName);
                    }
                }

                perDay.put(t, total);
            }
        }

        // (5) FSC inventories by bucket S[w,c,o,t] (integer CCLs)
        for (String w : fscs) {
            Map<Integer, Map<String, Map<String, Integer>>> perDay = fscInventoryByBucket.get(w);

            for (int t = 1; t <= H; t++) {
                Map<String, Map<String, Integer>> byCcl = new LinkedHashMap<>();

                for (String c : cclTypes) {
                    Map<String, Integer> byOuType = new LinkedHashMap<>();

                    for (OuType o : OuType.values()) {
                        String varName = nameS(w, c, o, t);
                        int val = roundInt(getX(model, varName));
                        byOuType.put(o.toString(), val); // assumes your enum names are already Vust/AT/Gn/PInf or overridden toString
                    }

                    byCcl.put(c, byOuType);
                }

                perDay.put(t, byCcl);
            }
        }

        return new ResupplySolution(
                obj,
                trucksMSC,
                trucksFSC,
                dailyMSC,
                dailyFSC,
                ouInventory,
                fscInventoryTotal,
                fscInventoryByBucket,
                shipments
        );
    }

    public static void writeCsvs(ResupplySolution sol, Instance inst, int H, Path outputDir) throws IOException {
        writeTrucksCsv(sol, H, outputDir.resolve("trucks.csv"));
        writeInventoryOuCsv(sol, inst, H, outputDir.resolve("inventory_ou.csv"));
        writeShipmentsCsv(sol, outputDir.resolve("shipments.csv"));
        writeInventoryFscTotalCsv(sol, inst, H, outputDir.resolve("inventory_fsc_total.csv"));
        writeInventoryFscDetailCsv(sol, inst, H, outputDir.resolve("inventory_fsc_detail.csv"));
    }

    // CSV 1: trucks.csv
    private static void writeTrucksCsv(ResupplySolution sol, int H, Path file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        String[] header = new String[]{"location", "day", "used", "capacity", "slack"};

        // MSC
        for (int t = 1; t <= H; t++) {
            int used = sol.dailyMSCDepartures.getOrDefault(t, 0);
            int cap = sol.trucksMSC;
            rows.add(new String[]{
                    "MSC",
                    String.valueOf(t),
                    String.valueOf(used),
                    String.valueOf(cap),
                    String.valueOf(cap - used)
            });
        }

        // FSCs
        for (var e : sol.trucksFSC.entrySet()) {
            String w = e.getKey();
            int cap = e.getValue();
            Map<Integer, Integer> usedMap = sol.dailyFSCDepartures.getOrDefault(w, Map.of());

            for (int t = 1; t <= H; t++) {
                int used = usedMap.getOrDefault(t, 0);
                rows.add(new String[]{
                        w,
                        String.valueOf(t),
                        String.valueOf(used),
                        String.valueOf(cap),
                        String.valueOf(cap - used)
                });
            }
        }

        CsvWriter.writeCsv(file, header, rows);
    }

    // CSV 2: inventory_ou.csv
    private static void writeInventoryOuCsv(ResupplySolution sol, Instance inst, int H, Path file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        String[] header = new String[]{"ou", "product", "day", "inventory", "demand", "capacity", "slack"};

        // Build lookup for demand/capacity
        Map<String, OperatingUnit> ouByName = new HashMap<>();
        for (OperatingUnit ou : inst.operatingUnits) {
            ouByName.put(ou.operatingUnit, ou);
        }

        for (var ouEntry : sol.ouInventory.entrySet()) {
            String ouName = ouEntry.getKey();
            OperatingUnit ou = ouByName.get(ouName);
            if (ou == null) continue;

            for (int t = 1; t <= H; t++) {
                Map<String, Double> invByProd = ouEntry.getValue().get(t);
                if (invByProd == null) continue;

                // FW
                addInventoryRow(rows, ouName, "FW", t,
                        invByProd.get("FW"),
                        (double) ou.dailyFoodWaterKg,
                        (double) ou.maxFoodWaterKg
                );
                // FUEL
                addInventoryRow(rows, ouName, "FUEL", t,
                        invByProd.get("FUEL"),
                        (double) ou.dailyFuelKg,
                        (double) ou.maxFuelKg
                );
                // AMMO
                addInventoryRow(rows, ouName, "AMMO", t,
                        invByProd.get("AMMO"),
                        (double) ou.dailyAmmoKg,
                        (double) ou.maxAmmoKg
                );
            }
        }

        CsvWriter.writeCsv(file, header, rows);
    }

    private static void addInventoryRow(List<String[]> rows, String ou, String product, int day,
                                        Double inventory, double demand, double cap) {
        double inv = (inventory == null) ? Double.NaN : inventory;
        rows.add(new String[]{
                ou,
                product,
                String.valueOf(day),
                String.valueOf(inv),
                String.valueOf(demand),
                String.valueOf(cap),
                String.valueOf(inv - demand)
        });
    }

    // CSV 3: shipments.csv
    private static void writeShipmentsCsv(ResupplySolution sol, Path file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        String[] header = new String[]{"from", "to", "cclType", "day", "quantity", "ouType"};

        for (Shipment s : sol.shipments) {
            rows.add(new String[]{
                    s.from(),
                    s.to(),
                    s.cclType(),
                    String.valueOf(s.day()),
                    String.valueOf(s.quantity()),
                    s.ouType()
            });
        }

        CsvWriter.writeCsv(file, header, rows);
    }

    private static void writeInventoryFscTotalCsv(ResupplySolution sol, Instance inst, int H, Path file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        String[] header = new String[]{"fsc", "day", "total_ccls", "capacity", "slack"};

        // FSC capacities are in inst.sourceCapacities, stored in CCL units
        for (var e : sol.fscInventoryTotal.entrySet()) {
            String w = e.getKey();
            int cap = inst.sourceCapacities.get(w).maxStorageCapCcls;

            for (int t = 1; t <= H; t++) {
                double total = e.getValue().getOrDefault(t, 0.0);
                rows.add(new String[]{
                        w,
                        String.valueOf(t),
                        String.valueOf((int) Math.round(total)),
                        String.valueOf(cap),
                        String.valueOf(cap - (int) Math.round(total))
                });
            }
        }

        CsvWriter.writeCsv(file, header, rows);
    }

    private static void writeInventoryFscDetailCsv(ResupplySolution sol, Instance inst, int H, Path file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        String[] header = new String[]{"fsc", "day", "cclType", "ouType", "ccls"};

        for (var eFsc : sol.fscInventoryByBucket.entrySet()) {
            String w = eFsc.getKey();

            for (int t = 1; t <= H; t++) {
                Map<String, Map<String, Integer>> byCcl = eFsc.getValue().get(t);
                if (byCcl == null) continue;

                for (var eCcl : byCcl.entrySet()) {
                    String c = eCcl.getKey();
                    Map<String, Integer> byOuType = eCcl.getValue();

                    for (var eType : byOuType.entrySet()) {
                        String ouType = eType.getKey();
                        int val = eType.getValue();

                        rows.add(new String[]{
                                w,
                                String.valueOf(t),
                                c,
                                ouType,
                                String.valueOf(val)
                        });
                    }
                }
            }
        }

        CsvWriter.writeCsv(file, header, rows);
    }

    // Helpers: variable naming & extraction
    private static double getX(GRBModel model, String varName) throws GRBException {
        GRBVar v = model.getVarByName(varName);
        if (v == null) {
            throw new IllegalStateException("Variable not found by name: " + varName);
        }
        return v.get(GRB.DoubleAttr.X);
    }

    private static int roundInt(double x) {
        return (int) Math.round(x);
    }

    private static String nameX(String w, String i, String c, int t) {
        return "x_" + noSpace(w) + "_" + noSpace(i) + "_" + noSpace(c) + "_t" + t;
    }

    private static String nameY(String w, String c, OuType o, int t) {
        return "y_" + noSpace(w) + "_" + noSpace(c) + "_" + o + "_t" + t;
    }

    private static String nameZ(String c, int t) {
        return "z_" + noSpace(c) + "_t" + t;
    }

    private static String nameI(String ou, String prod, int t) {
        return "I_" + noSpace(ou) + "_" + prod + "_t" + t;
    }

    private static String nameS(String w, String c, OuType o, int t) {
        return "S_" + noSpace(w) + "_" + noSpace(c) + "_" + o + "_t" + t;
    }

    private static String noSpace(String s) {
        return s == null ? "" : s.replace(" ", "");
    }
}
