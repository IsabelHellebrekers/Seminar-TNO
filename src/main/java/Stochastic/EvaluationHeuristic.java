package Stochastic;

import Objects.*;

import java.util.*;

public class EvaluationHeuristic {
    private static final int IDX_FW = 0;
    private static final int IDX_FUEL = 1;
    private static final int IDX_AMMO = 2;

    private static final double MAX_MULT_FW = 1.2;
    private static final double MAX_MULT_FUEL = 2.5;
    private static final double MAX_MULT_AMMO = 2.0;

    private static final double EPS = 1e-9;

    private record UsedKey(String fsc, String ouType, int cclType) {
    }

    public static final class ScenarioResult {
        public final boolean hasStockout;
        public final double totalStockoutKg;
        public final List<Stockout> stockouts;

        public ScenarioResult(boolean hasStockout, double totalStockoutKg, List<Stockout> stockouts) {
            this.hasStockout = hasStockout;
            this.totalStockoutKg = totalStockoutKg;
            this.stockouts = stockouts;
        }
    }

    public static final class EvaluationSummary {
        public final int totalScenarios;
        public final int scenariosWithoutStockout;
        public final double noStockoutPercentage;
        public final double avgTotalStockoutKg;

        private EvaluationSummary(int totalScenarios, int scenariosWithoutStockout, double noStockoutPercentage,
                double avgTotalStockoutKg) {
            this.totalScenarios = totalScenarios;
            this.scenariosWithoutStockout = scenariosWithoutStockout;
            this.noStockoutPercentage = noStockoutPercentage;
            this.avgTotalStockoutKg = avgTotalStockoutKg;
        }
    }

    public static EvaluationSummary evaluate(Instance data, int M, Map<String, Integer> K, int nScenarios,
            long baseSeed) {
        int noStockoutCount = 0;
        double sumStockoutKg = 0.0;

        for (int s = 1; s <= nScenarios; s++) {
            ScenarioResult result = evaluateSingleScenario(data, M, K, baseSeed + s);
            if (!result.hasStockout) {
                noStockoutCount++;
            }
            sumStockoutKg += result.totalStockoutKg;
        }

        double noStockoutPercentage = (double) noStockoutCount / nScenarios * 100.0;
        double avgStockoutKg = sumStockoutKg / nScenarios;

        return new EvaluationSummary(nScenarios, noStockoutCount, noStockoutPercentage, avgStockoutKg);
    }

    public static ScenarioResult evaluateSingleScenario(Instance data, int M, Map<String, Integer> K, long scenarioSeed) {
        Random rng = new Random(scenarioSeed);

        Map<String, OperatingUnit> ouByName = new HashMap<>();
        for (OperatingUnit ou : data.operatingUnits) {
            ouByName.put(ou.operatingUnitName, ou);
        }

        Map<String, double[]> ouMaxDemand = new HashMap<>();
        for (OperatingUnit ou : data.operatingUnits) {
            ouMaxDemand.put(ou.operatingUnitName, computeOuNextDayMaxDemand(ou));
        }

        OperatingUnit vust = ouByName.get("VUST");

        double[] vustTarget = computeVustNextDayMaxDemandTarget(vust);

        Map<String, List<OperatingUnit>> ousByFsc = new HashMap<>();
        for (FSC f : data.FSCs) {
            ousByFsc.put(f.FSCname, new ArrayList<>());
        }

        for (OperatingUnit ou : data.operatingUnits) {
            if (ou.operatingUnitName.equals("VUST")) {
                continue;
            }
            ousByFsc.get(ou.source).add(ou);
        }

        Map<String, double[]> ouInv = initOuInventory(data);
        Map<String, Map<String, int[]>> fscInv = initFscInventory(data);

        List<Stockout> stockouts = new ArrayList<>();
        double totalStockoutKg = 0.0;

        Sampling sampler = new Sampling(scenarioSeed);

        // Simulate days t = 1,...,T
        for (int t = 1; t <= data.timeHorizon; t++) {

            // 1) Realize and consume demand on day t
            for (OperatingUnit ou : data.operatingUnits) {
                double[] inv = ouInv.get(ou.operatingUnitName);

                double dFW = sampler.uniform() * ou.dailyFoodWaterKg;
                double dFUEL = sampler.binomial() * ou.dailyFuelKg;
                double dAMMO = sampler.triangular() * ou.dailyAmmoKg;

                totalStockoutKg += consumeAndRecordStockout(stockouts, ou.operatingUnitName, "FW", dFW, t, inv, IDX_FW);
                totalStockoutKg += consumeAndRecordStockout(stockouts, ou.operatingUnitName, "FUEL", dFUEL, t, inv, IDX_FUEL);
                totalStockoutKg += consumeAndRecordStockout(stockouts, ou.operatingUnitName, "AMMO", dAMMO, t, inv, IDX_AMMO);
            }

            // No dispatch on the final day
            if (t == data.timeHorizon) {
                break;
            }

            // 2) Scheduled arrivals decided today
            Map<String, double[]> scheduledDeliveries = new HashMap<>();
            for (OperatingUnit ou : data.operatingUnits) {
                scheduledDeliveries.put(ou.operatingUnitName, new double[]{0.0, 0.0, 0.0});
            }

            Map<UsedKey, Integer> usedToday = new HashMap<>();

            // 3) Downstream : FSC -> OU
            for (FSC fsc : data.FSCs) {
                String w = fsc.FSCname;
                int trucks = K.getOrDefault(w, 0);
                if (trucks <= 0) {
                    continue;
                }

                List<OperatingUnit> candidates = ousByFsc.getOrDefault(w, Collections.emptyList());
                if (candidates.isEmpty()) {
                    continue;
                }

                Set<String> blocked = new HashSet<>();

                for (int kTruck = 0; kTruck < trucks; kTruck++) {
                    OperatingUnit chosenOu = selectMostUrgentOU(candidates, ouInv, scheduledDeliveries, blocked, ouMaxDemand, rng);
                    if (chosenOu == null) {
                        break;
                    }

                    int chosenC = selectBestFeasibleCCLTypeAllProducts(
                        chosenOu,
                        fscInv.get(w),
                        ouInv.get(chosenOu.operatingUnitName),
                        scheduledDeliveries.get(chosenOu.operatingUnitName),
                        data.cclTypes,
                        ouMaxDemand.get(chosenOu.operatingUnitName),
                        rng
                    );


                    if (chosenC == -1) {
                        blocked.add(chosenOu.operatingUnitName);
                        kTruck--;
                        if (blocked.size() >= candidates.size()) {
                            break;
                        }
                        continue;
                    }

                    String ouType = chosenOu.ouType;
                    fscInv.get(w).get(ouType)[chosenC - 1]--;

                    addCclToscheduledDeliveries(scheduledDeliveries.get(chosenOu.operatingUnitName), data.cclTypes, chosenC);

                    usedToday.merge(new UsedKey(w, ouType, chosenC), 1, Integer::sum);
                }
            }

            // 4) Upstream : first fill VUST to target, then fill FSCs
            int mRemaining = Math.max(0, M);

            // 4a) MSC -> VUST
            {
                double[] inv = ouInv.get("VUST");
                double[] add = scheduledDeliveries.get("VUST");

                while (mRemaining > 0 && !meetsTarget(inv, add, vustTarget)) {
                    int bestC = selectBestFeasibleCCLTypeForVustTarget(vust, inv, add, data.cclTypes, vustTarget);
                    if (bestC == -1) {
                        break;
                    }

                    addCclToscheduledDeliveries(add, data.cclTypes, bestC);
                    mRemaining--;
                }
            }

            // 4b) MSC -> FSC
            while (mRemaining > 0) {
                RefillChoice choice = selectLowestRatioRefill(data.FSCs, fscInv);
                if (choice == null) break;

                FSC f = choice.fsc;
                if (totalCclsAtFsc(f, fscInv) >= f.maxStorageCapCcls) break;

                fscInv.get(f.FSCname).get(choice.ouType)[choice.cclType - 1]++;
                mRemaining--;
            }


            // 4c) MSC -> FSC buffer build up with remaning MSC trucks (IRRELEVANT I THINK)
            if (mRemaining > 0) {
                List<String> nonVustOuTypes = new ArrayList<>();
                for (String o : data.ouTypes) {
                    if (!o.equals("VUST")) {
                        nonVustOuTypes.add(o);
                    }
                }

                while (mRemaining > 0) {
                    FSC targetFsc = selectMostEmptyFsc(data.FSCs, fscInv);
                    if (targetFsc == null) {
                        break;
                    }

                    String w = targetFsc.FSCname;

                    int totalNow = totalCclsAtFsc(targetFsc, fscInv);
                    if (totalNow >= targetFsc.maxStorageCapCcls) {
                        mRemaining--;
                        continue;
                    }

                    Bucket bucket = selectTopUpBucketForFsc(w, usedToday, nonVustOuTypes);
                    String o = bucket.ouType;
                    int c = bucket.cclType;

                    fscInv.get(w).get(o)[c - 1]++;
                    mRemaining--;
                }
            }

            // 5)  Apply arrivals to obtain start-of-day inventories for day t+1
            for (OperatingUnit ou : data.operatingUnits) {
                double[] inv = ouInv.get(ou.operatingUnitName);
                double[] add = scheduledDeliveries.get(ou.operatingUnitName);

                inv[IDX_FW] = Math.min(inv[IDX_FW] + add[IDX_FW], ou.maxFoodWaterKg);
                inv[IDX_FUEL] = Math.min(inv[IDX_FUEL] + add[IDX_FUEL], ou.maxFuelKg);
                inv[IDX_AMMO] = Math.min(inv[IDX_AMMO] + add[IDX_AMMO], ou.maxAmmoKg);
            }
        }

        boolean hasStockout = !stockouts.isEmpty();
        return new ScenarioResult(hasStockout, totalStockoutKg, stockouts);
    }

    // --- Helper methods for Vust ---

    private static double[] computeVustNextDayMaxDemandTarget(OperatingUnit vust) {
        double targetFW = Math.min(vust.maxFoodWaterKg, vust.dailyFoodWaterKg * MAX_MULT_FW * 0.9);
        double targetFUEL = Math.min(vust.maxFuelKg, vust.dailyFuelKg * MAX_MULT_FUEL * 0.9);
        double targetAMMO = Math.min(vust.maxAmmoKg, vust.dailyAmmoKg * MAX_MULT_AMMO * 0.9);

        return new double[] { targetFW, targetFUEL, targetAMMO };
    }

    private static boolean meetsTarget(double[] inv, double[] add, double[] target) {
        return inv[IDX_FW] + add[IDX_FW] + EPS >= target[IDX_FW]
                && inv[IDX_FUEL] + add[IDX_FUEL] + EPS >= target[IDX_FUEL]
                && inv[IDX_AMMO] + add[IDX_AMMO] + EPS >= target[IDX_AMMO];
    }

    private static int selectBestFeasibleCCLTypeForVustTarget(
            OperatingUnit vust,
            double[] inv,
            double[] add,
            List<CCLpackage> cclTypes,
            double[] target) {
        double before = relativeDeficitSum(inv, add, target);

        int bestC = -1;
        double bestAfter = Double.POSITIVE_INFINITY;

        for (CCLpackage ccl : cclTypes) {
            int c = ccl.type;
            // if (c < 1 || c > 3) {
            //     continue;
            // }

            if (!fitsCapacity(vust, inv, add, ccl)) {
                continue;
            }

            double after = relativeDeficitSumAfter(inv, add, target, ccl);
            if (after + EPS < bestAfter) {
                bestAfter = after;
                bestC = c;
            }
        }

        if (bestC != -1 && bestAfter + EPS < before) {
            return bestC;
        }

        return -1;
    }

    private static double relativeDeficitSum(double[] inv, double[] add, double[] target) {
        double fwLvl = inv[IDX_FW] + add[IDX_FW];
        double fuelLvl = inv[IDX_FUEL] + add[IDX_FUEL];
        double ammoLvl = inv[IDX_AMMO] + add[IDX_AMMO];

        double dfw = Math.max(0.0, target[IDX_FW] - fwLvl) / Math.max(1.0, target[IDX_FW]);
        double dfuel = Math.max(0.0, target[IDX_FUEL] - fuelLvl) / Math.max(1.0, target[IDX_FUEL]);
        double dammo = Math.max(0.0, target[IDX_AMMO] - ammoLvl) / Math.max(1.0, target[IDX_AMMO]);

        return dfw + dfuel + dammo;
    }

    private static double relativeDeficitSumAfter(double[] inv, double[] add, double[] target, CCLpackage ccl) {
        double fwLvl = inv[IDX_FW] + add[IDX_FW] + ccl.foodWaterKg;
        double fuelLvl = inv[IDX_FUEL] + add[IDX_FUEL] + ccl.fuelKg;
        double ammoLvl = inv[IDX_AMMO] + add[IDX_AMMO] + ccl.ammoKg;

        double dfw = Math.max(0.0, target[IDX_FW] - fwLvl) / Math.max(1.0, target[IDX_FW]);
        double dfuel = Math.max(0.0, target[IDX_FUEL] - fuelLvl) / Math.max(1.0, target[IDX_FUEL]);
        double dammo = Math.max(0.0, target[IDX_AMMO] - ammoLvl) / Math.max(1.0, target[IDX_AMMO]);

        return dfw + dfuel + dammo;
    }

    // --- Helper methods for demand and inventories

    private static double consumeAndRecordStockout(
            List<Stockout> stockouts,
            String ouName,
            String product,
            double demand,
            int day,
            double[] inv,
            int idx) {
        inv[idx] -= demand;
        if (inv[idx] < -EPS) {
            double amount = -inv[idx];
            stockouts.add(new Stockout(ouName, product, amount, day));
            inv[idx] = 0.0;
            return amount;
        }
        if (inv[idx] < 0.0) {
            inv[idx] = 0.0;
        }

        return 0.0;
    }

    private static Map<String, double[]> initOuInventory(Instance data) {
        Map<String, double[]> ouInv = new HashMap<>();
        for (OperatingUnit ou : data.operatingUnits) {
            ouInv.put(ou.operatingUnitName, new double[] { ou.maxFoodWaterKg, ou.maxFuelKg, ou.maxAmmoKg });
        }
        return ouInv;
    }

    private static Map<String, Map<String, int[]>> initFscInventory(Instance data) {
        Map<String, Map<String, int[]>> inv = new HashMap<>();
        for (FSC fsc : data.FSCs) {
            Map<String, int[]> byType = new HashMap<>();
            for (Map.Entry<String, int[]> e : fsc.initialStorageLevels.entrySet()) {
                byType.put(e.getKey(), e.getValue().clone());
            }
            for (String o : data.ouTypes) {
                if (o.equals("VUST")) {
                    continue;
                }
                byType.computeIfAbsent(o, k -> new int[] { 0, 0, 0 });
            }
            inv.put(fsc.FSCname, byType);
        }

        return inv;
    }

    // --- Helper methods for downstream dispatching ---

    private static OperatingUnit selectMostUrgentOU(
            List<OperatingUnit> candidates,
            Map<String, double[]> ouInv,
            Map<String, double[]> scheduledDeliveries,
            Set<String> blocked,
            Map<String, double[]> ouMaxDemand,
            Random rng
        ) {
        OperatingUnit best = null;
        double bestRatio = Double.POSITIVE_INFINITY;

        int ties = 0;

        for (OperatingUnit ou : candidates) {
            if (blocked.contains(ou.operatingUnitName)) {
                continue;
            }

            double[] inv = ouInv.get(ou.operatingUnitName);
            double[] add = scheduledDeliveries.get(ou.operatingUnitName);
            double[] dmax = ouMaxDemand.get(ou.operatingUnitName);

            double ratio = minDemandCoverRatio(inv, add, dmax);

            if (ratio + EPS < bestRatio) {
                bestRatio = ratio;
                best = ou;
                ties = 1;
            } else if (Math.abs(ratio - bestRatio) <= EPS) {
                ties++;
                if (rng.nextInt(ties) == 0) {
                    best = ou;
                }
            } 
        }

        return best;
    }

    private static double minDemandCoverRatio(double[] inv, double[] add, double[] dmax) {
        double rFW = (inv[IDX_FW] + add[IDX_FW]) / Math.max(1.0, dmax[IDX_FW]);
        double rFUEL = (inv[IDX_FUEL] + add[IDX_FUEL]) / Math.max(1.0, dmax[IDX_FUEL]);
        double rAMMO = (inv[IDX_AMMO] + add[IDX_AMMO]) / Math.max(1.0, dmax[IDX_AMMO]);
        return Math.min(rFW, Math.min(rFUEL, rAMMO));
    }

    private static double[] computeOuNextDayMaxDemand(OperatingUnit ou) {
        // QUANTILES
        // double dFW = ou.dailyFoodWaterKg * QUANT_FW;
        // double dFUEL = ou.dailyFuelKg * QUANT_FUEL;
        // double dAMMO = ou.dailyAmmoKg * QUANT_AMMO;

        // MAX
        double dFW = ou.dailyFoodWaterKg * MAX_MULT_FW;
        double dFUEL = ou.dailyFuelKg * MAX_MULT_FUEL;
        double dAMMO = ou.dailyAmmoKg * MAX_MULT_AMMO;

        return new double[] {dFW, dFUEL, dAMMO};
    }

    private static int selectBestFeasibleCCLTypeAllProducts(
            OperatingUnit ou,
            Map<String, int[]> fscInvByOuType,
            double[] inv,
            double[] add,
            List<CCLpackage> cclTypes,
            double[] dmax,
            Random rng
    ) {
        int[] stock = fscInvByOuType.get(ou.ouType);
        if (stock == null) return -1;

        int bestC = -1;
        double bestScore = -1.0;

        int ties = 0;

        for (CCLpackage ccl : cclTypes) {
            int c = ccl.type;
            if (stock[c - 1] <= 0) continue;

            if (!fitsCapacity(ou, inv, add, ccl)) continue;

            // NEW: score = reduction in normalized deficits to target (order-up-to target)
            double score = postDeliveryDeficitReductionScore(ou, inv, add, ccl, dmax);

            if (score > bestScore + EPS) {
                bestScore = score;
                bestC = c;
                ties = 1;
            } else if (Math.abs(score - bestScore) <= EPS) {
                ties++;
                if (rng.nextInt(ties) == 0) {
                    bestC = c;
                }
            }
        }
        return bestC;
    }

    private static double postDeliveryDeficitReductionScore(
            OperatingUnit ou,
            double[] inv,
            double[] add,
            CCLpackage ccl,
            double[] dmax
    ) {
        // Targets: do not exceed OU capacity, and do not exceed the "max demand" proxy (dmax)
        // ADJUSTED TARGET
        double targetFW   = Math.min(ou.maxFoodWaterKg, Math.max(0.0, 1.1 * dmax[IDX_FW]));
        double targetFUEL = Math.min(ou.maxFuelKg,      Math.max(0.0, 0.9 * dmax[IDX_FUEL]));
        double targetAMMO = Math.min(ou.maxAmmoKg,      Math.max(0.0, 1.4 * dmax[IDX_AMMO]));

        double before =
                normalizedDeficit(inv[IDX_FW]   + add[IDX_FW],   targetFW) +
                normalizedDeficit(inv[IDX_FUEL] + add[IDX_FUEL], targetFUEL) +
                normalizedDeficit(inv[IDX_AMMO] + add[IDX_AMMO], targetAMMO);

        double after =
                normalizedDeficit(inv[IDX_FW]   + add[IDX_FW]   + ccl.foodWaterKg, targetFW) +
                normalizedDeficit(inv[IDX_FUEL] + add[IDX_FUEL] + ccl.fuelKg,      targetFUEL) +
                normalizedDeficit(inv[IDX_AMMO] + add[IDX_AMMO] + ccl.ammoKg,      targetAMMO);

        // We maximize how much deficit we remove
        return before - after;
    }

    private static double normalizedDeficit(double level, double target) {
        // If target is 0 (or tiny), treat deficit as 0 to avoid division issues
        double denom = Math.max(1.0, target);
        return Math.max(0.0, target - level) / denom;
    }

    private static boolean fitsCapacity(OperatingUnit ou, double[] inv, double[] add, CCLpackage ccl) {
        double fw = inv[IDX_FW] + add[IDX_FW] + ccl.foodWaterKg;
        double fuel = inv[IDX_FUEL] + add[IDX_FUEL] + ccl.fuelKg;
        double ammo = inv[IDX_AMMO] + add[IDX_AMMO] + ccl.ammoKg;

        return fw <= ou.maxFoodWaterKg + EPS
                && fuel <= ou.maxFuelKg + EPS
                && ammo <= ou.maxAmmoKg + EPS;
    }

    private static void addCclToscheduledDeliveries(double[] add, List<CCLpackage> cclTypes, int cclType) {
        CCLpackage chosen = null;
        for (CCLpackage c : cclTypes) {
            if (c.type == cclType) {
                chosen = c;
                break;
            }
        }
        if (chosen != null) {
            add[IDX_FW] += chosen.foodWaterKg;
            add[IDX_FUEL] += chosen.fuelKg;
            add[IDX_AMMO] += chosen.ammoKg;
        }
    }

    // --- Helper methods for upstream dispatching ---

    private static int totalCclsAtFsc(FSC fsc, Map<String, Map<String, int[]>> fscInv) {
        Map<String, int[]> byType = fscInv.get(fsc.FSCname);
        if (byType == null) {
            return 0;
        }

        int total = 0;
        for (int[] arr : byType.values()) {
            total += arr[0] + arr[1] + arr[2];
        }
        return total;
    }

    private static final class RefillChoice {
        final FSC fsc;
        final String ouType;
        final int cclType;

        RefillChoice(FSC fsc, String ouType, int cclType) {
            this.fsc = fsc;
            this.ouType = ouType;
            this.cclType = cclType;
        }
    }

    private static RefillChoice selectLowestRatioRefill(List<FSC> fscs, Map<String, Map<String, int[]>> fscInv) {
        RefillChoice best = null;
        double bestRatio = Double.POSITIVE_INFINITY;

        for (FSC fsc : fscs) {
            if (totalCclsAtFsc(fsc, fscInv) >= fsc.maxStorageCapCcls) continue;

            Map<String, int[]> curByType = fscInv.get(fsc.FSCname);
            if (curByType == null) continue;

            for (Map.Entry<String, int[]> initEntry : fsc.initialStorageLevels.entrySet()) {
                String ouType = initEntry.getKey();
                if (ouType.equals("VUST")) continue;

                int[] initArr = initEntry.getValue();
                int[] curArr  = curByType.get(ouType);
                if (curArr == null) continue;

                for (int c = 1; c <= 3; c++) {
                    int init = initArr[c - 1];
                    if (init <= 0) continue;

                    int cur = curArr[c - 1];
                    double ratio = (double) cur / init;

                    if (ratio + EPS < bestRatio) {
                        bestRatio = ratio;
                        best = new RefillChoice(fsc, ouType, c);
                    }
                }
            }
        }
        return best;
    }

    // --- Helper methods for buffer build up ---

    private static final class Bucket {
        final String ouType;
        final int cclType;

        private Bucket(String ouType, int cclType) {
            this.ouType = ouType;
            this.cclType = cclType;
        }
    }

    private static FSC selectMostEmptyFsc(List<FSC> fscs, Map<String, Map<String, int[]>> fscInv) {
        FSC best = null;
        double bestRatio = Double.POSITIVE_INFINITY;

        for (FSC f : fscs) {
            int cap = Math.max(1, f.maxStorageCapCcls);
            int total = totalCclsAtFsc(f, fscInv);
            if (total >= f.maxStorageCapCcls) {
                continue;
            }

            double ratio = (double) total / cap;
            if (ratio < bestRatio) {
                bestRatio = ratio;
                best = f;
            }
        }
        return best;
    }

    private static Bucket selectTopUpBucketForFsc(
            String w,
            Map<UsedKey, Integer> usedToday,
            List<String> nonVustOuTypes) {
        String bestO = null;
        int bestC = 1;
        int bestUsed = -1;

        for (Map.Entry<UsedKey, Integer> e : usedToday.entrySet()) {
            UsedKey k = e.getKey();
            if (!k.fsc().equals(w))
                continue;

            int used = e.getValue() == null ? 0 : e.getValue();
            if (used <= 0)
                continue;

            if (used > bestUsed) {
                bestUsed = used;
                bestO = k.ouType();
                bestC = k.cclType();
            }
        }

        if (bestO != null) {
            return new Bucket(bestO, bestC);
        }

        return new Bucket(nonVustOuTypes.get(0), 1);
    }
}
