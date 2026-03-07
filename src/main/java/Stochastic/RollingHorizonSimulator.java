package Stochastic;

import com.gurobi.gurobi.*;
import DataUtils.InstanceCreator;
import Objects.*;

import java.util.*;

public class RollingHorizonSimulator {

    private static final class StockoutInfo {
        boolean stockout;
        int day;
        String ou;
        String product;
    }

    public static List<Result> solveInstancesRollingHorizon(
            List<Instance> instances,
            double M,
            Map<String, Double> K,
            int windowLength,
            Map<String, Double> forecastMultiplier,
            boolean verboseMILP
    ) {
        if (instances == null || instances.isEmpty()) return Collections.emptyList();

        List<Result> results = new ArrayList<>();
        GRBEnv env = null;

        int nrStockouts = 0;
        int total = 1000;

        try {
            env = new GRBEnv(true);
            env.set("logFile", "gurobi_batch.log");
            env.start();

            Instance inst = instances.get(0);

            for (int idx = 0; idx < total; idx++) {
                int percent = (int) Math.round(100.0 * (idx + 1) / total);

                StockoutInfo info = runOneRollout(inst, env, M, K, windowLength, forecastMultiplier, verboseMILP);

                if (info.stockout) nrStockouts++;

                double stockoutPercent = 100.0 * nrStockouts / (idx + 1);

                String stockoutInfo;
                if (info.stockout) {
                    stockoutInfo = String.format(
                            " | Stockout: Day %d, OU %s, Product %s                          ",
                            info.day, info.ou, info.product
                    );
                } else {
                    stockoutInfo = " | No stockout occurred.                                 ";
                }

                System.out.printf(
                        "\rProgress: %d%% (%d / %d instances) | Stockouts so far: %d (%.1f%%)%s",
                        percent,
                        idx + 1,
                        total,
                        nrStockouts,
                        stockoutPercent,
                        stockoutInfo
                );
            }
            System.out.flush();

            return results;

        } catch (Exception e) {
            throw new RuntimeException("Failed in solveInstancesRollingHorizon(): " + e.getMessage(), e);
        } finally {
            if (env != null) {
                try { env.dispose(); } catch (Exception ignored) {}
            }
        }
    }

    private static StockoutInfo runOneRollout(
            Instance inst,
            GRBEnv env,
            double M,
            Map<String, Double> K,
            int windowLength,
            Map<String, Double> forecastMultiplier,
            boolean verboseMILP
    ) throws GRBException {

        // start-of-day inventories for day 1 (match your original initialization)
        Map<String, Map<String, Double>> Icur = initOuInventories(inst);
        Map<String, Map<String, Map<Integer, Integer>>> Scur = initFscInventories(inst);

        StockoutInfo info = new StockoutInfo();
        info.stockout = false;
        info.day = -1;

        for (int day = 1; day <= inst.timeHorizon; day++) {

            // Solve window MILP for [day .. day+H-1]
            WindowMILP milp = new WindowMILP(
                    inst, env, M, K, day, windowLength, Icur, Scur, forecastMultiplier, verboseMILP
            );

            try {
                milp.solve();
            } catch (Exception ex) {
                // Treat infeasibility/no-solution as failure for this rollout
                info.stockout = true;
                info.day = day;
                info.ou = "N/A";
                info.product = "INFEASIBLE";
                milp.dispose();
                return info;
            }

            // Read day-1 decisions
            Map<String, Map<Integer, Integer>> xToday = new HashMap<>(); // ou -> cType -> trucks
            Map<String, Map<String, Map<Integer, Integer>>> yToday = new HashMap<>(); // fsc -> o -> cType -> trucks
            Map<Integer, Integer> zToday = new HashMap<>();

            // x
            for (OperatingUnit ou : inst.operatingUnits) {
                if (ou.operatingUnitName.equals("VUST")) continue;
                Map<Integer, Integer> perC = new HashMap<>();
                for (CCLpackage c : inst.cclTypes) {
                    int trucks = milp.getX(ou.source, ou.operatingUnitName, c.type);
                    perC.put(c.type, trucks);
                }
                xToday.put(ou.operatingUnitName, perC);
            }

            // y
            for (FSC w : inst.FSCs) {
                Map<String, Map<Integer, Integer>> perType = new HashMap<>();
                for (String o : inst.ouTypes) {
                    Map<Integer, Integer> perC = new HashMap<>();
                    for (CCLpackage c : inst.cclTypes) {
                        perC.put(c.type, milp.getY(w.FSCname, c.type, o));
                    }
                    perType.put(o, perC);
                }
                yToday.put(w.FSCname, perType);
            }

            // z
            for (CCLpackage c : inst.cclTypes) {
                zToday.put(c.type, milp.getZ(c.type));
            }

            milp.dispose();

            // Realized demand for this day (your original Sampling)
            Map<String, Map<String, Double>> realized = sampleRealizedDemand(inst);

            // Stockout check: start-of-day inventory vs realized demand
            for (OperatingUnit ou : inst.operatingUnits) {
                String i = ou.operatingUnitName;
                for (String p : inst.products) {
                    double inv = Icur.get(i).get(p);
                    double dem = realized.get(i).getOrDefault(p, 0.0);
                    if (inv < dem) {
                        info.stockout = true;
                        info.day = day;
                        info.ou = i;
                        info.product = p;
                        return info;
                    }
                }
            }

            // Advance to next day:
            // 1) consume demand during day
            // 2) add deliveries shipped today (arrive next day)
            Map<String, Map<String, Double>> Inext = advanceOu(inst, Icur, realized, xToday, zToday);
            Map<String, Map<String, Map<Integer, Integer>>> Snext = advanceFsc(inst, Scur, xToday, yToday);

            Icur = Inext;
            Scur = Snext;
        }

        return info;
    }

    // ---------- state init (same as your current MILP) ----------
    private static Map<String, Map<String, Double>> initOuInventories(Instance inst) {
        Map<String, Map<String, Double>> I0 = new HashMap<>();
        for (OperatingUnit ou : inst.operatingUnits) {
            Map<String, Double> perP = new HashMap<>();
            perP.put("FW", ou.maxFoodWaterKg);
            perP.put("FUEL", ou.maxFuelKg);
            perP.put("AMMO", ou.maxAmmoKg);
            for (String p : inst.products) perP.putIfAbsent(p, 0.0);
            I0.put(ou.operatingUnitName, perP);
        }
        return I0;
    }

    private static Map<String, Map<String, Map<Integer, Integer>>> initFscInventories(Instance inst) {
        Map<String, Map<String, Map<Integer, Integer>>> S0 = new HashMap<>();
        for (FSC w : inst.FSCs) {
            Map<String, Map<Integer, Integer>> perType = new HashMap<>();
            for (String o : inst.ouTypes) {
                if (o.equals("VUST")) continue;
                Map<Integer, Integer> perC = new HashMap<>();
                int[] arr = w.initialStorageLevels.get(o);
                for (CCLpackage c : inst.cclTypes) {
                    perC.put(c.type, arr[c.type - 1]);
                }
                perType.put(o, perC);
            }
            S0.put(w.FSCname, perType);
        }
        return S0;
    }

    // ---------- realized demand sampling (same distributions as your code) ----------
    private static Map<String, Map<String, Double>> sampleRealizedDemand(Instance inst) {
        Map<String, Map<String, Double>> realized = new HashMap<>();
        for (OperatingUnit ou : inst.operatingUnits) {
            Sampling rand = new Sampling();
            Map<String, Double> perP = new HashMap<>();
            perP.put("FW", rand.uniform() * ou.dailyFoodWaterKg);
            perP.put("FUEL", rand.binomial() * ou.dailyFuelKg);
            perP.put("AMMO", rand.triangular() * ou.dailyAmmoKg);
            for (String p : inst.products) perP.putIfAbsent(p, 0.0);
            realized.put(ou.operatingUnitName, perP);
        }
        return realized;
    }

    // ---------- state transitions ----------
    private static Map<String, Map<String, Double>> advanceOu(
            Instance inst,
            Map<String, Map<String, Double>> Icur,
            Map<String, Map<String, Double>> realized,
            Map<String, Map<Integer, Integer>> xToday,
            Map<Integer, Integer> zToday
    ) {
        Map<String, Map<String, Double>> Inext = new HashMap<>();

        for (OperatingUnit ou : inst.operatingUnits) {
            String i = ou.operatingUnitName;
            Map<String, Double> next = new HashMap<>();

            for (String p : inst.products) {
                double invStart = Icur.get(i).get(p);
                double dem = realized.get(i).getOrDefault(p, 0.0);

                double delivered = 0.0;
                for (CCLpackage c : inst.cclTypes) {
                    double content = contentKg(c, p);
                    if (!i.equals("VUST")) {
                        int trucks = xToday.get(i).getOrDefault(c.type, 0);
                        delivered += trucks * content;
                    } else {
                        int trucks = zToday.getOrDefault(c.type, 0);
                        delivered += trucks * content;
                    }
                }

                double invNext = invStart - dem + delivered;
                next.put(p, invNext);
            }

            Inext.put(i, next);
        }

        return Inext;
    }

    private static Map<String, Map<String, Map<Integer, Integer>>> advanceFsc(
            Instance inst,
            Map<String, Map<String, Map<Integer, Integer>>> Scur,
            Map<String, Map<Integer, Integer>> xToday,
            Map<String, Map<String, Map<Integer, Integer>>> yToday
    ) {
        Map<String, Map<String, Map<Integer, Integer>>> Snext = new HashMap<>();

        // group OUs by source/type
        Map<String, Map<String, List<String>>> ousBySourceType = new HashMap<>();
        for (OperatingUnit ou : inst.operatingUnits) {
            if (ou.operatingUnitName.equals("VUST")) continue;
            ousBySourceType
                    .computeIfAbsent(ou.source, kk -> new HashMap<>())
                    .computeIfAbsent(ou.ouType, kk -> new ArrayList<>())
                    .add(ou.operatingUnitName);
        }

        for (FSC wObj : inst.FSCs) {
            String w = wObj.FSCname;
            Map<String, Map<Integer, Integer>> perTypeNext = new HashMap<>();

            for (String o : inst.ouTypes) {
                if (o.equals("VUST")) continue;

                Map<Integer, Integer> perCNext = new HashMap<>();

                for (CCLpackage c : inst.cclTypes) {
                    int sStart = Scur.get(w).get(o).get(c.type);

                    int yIn = yToday.get(w).get(o).getOrDefault(c.type, 0);

                    int xOut = 0;
                    for (String ouName : ousBySourceType.getOrDefault(w, Map.of()).getOrDefault(o, List.of())) {
                        xOut += xToday.get(ouName).getOrDefault(c.type, 0);
                    }

                    perCNext.put(c.type, sStart + yIn - xOut);
                }

                perTypeNext.put(o, perCNext);
            }

            Snext.put(w, perTypeNext);
        }

        return Snext;
    }

    private static double contentKg(CCLpackage c, String p) {
        return switch (p) {
            case "FW" -> c.foodWaterKg;
            case "FUEL" -> c.fuelKg;
            case "AMMO" -> c.ammoKg;
            default -> 0.0;
        };
    }

    public static void main(String[] args) {
        int H = 10;
        Map<String, Double> mult = Map.of("FW", 1.0, "FUEL", 1.1, "AMMO", 1.1);

        solveInstancesRollingHorizon(
                InstanceCreator.createFDInstance(),
                79.0,
                Map.of("FSC_1", 48.0, "FSC_2", 35.0),
                H,
                mult,
                false
        );
    }
}