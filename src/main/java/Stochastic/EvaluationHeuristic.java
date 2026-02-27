package Stochastic;

import Objects.*;

import java.util.*;

import DataUtils.InstanceCreator;

public class EvaluationHeuristic {
    private static final int IDX_FW = 0;
    private static final int IDX_FUEL = 1;
    private static final int IDX_AMMO = 2;

    private static final double MAX_MULT_FW = 1.2;
    private static final double MAX_MULT_FUEL = 2.5;
    private static final double MAX_MULT_AMMO = 2.0;

    private static final double BALANCE_PENALTY_LAMBDA = 1.0;

    private static final double EPS = 1e-9;

    public record TargetWeights(double fw, double fuel, double ammo) {
    }

    public record WeightConfig(TargetWeights ou, TargetWeights vust) {
    }

    public record newCCLComposition(int fwKg, int fuelKg, int ammoKg) {
    }

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

        @Override
        public String toString() {
            return String.format(
                    "EvaluationSummary{totalScenarios=%d, scenariosWithoutStockout=%d, noStockoutPercentage=%.2f%%, avgTotalStockoutKg=%.2f}",
                    totalScenarios, scenariosWithoutStockout, noStockoutPercentage, avgTotalStockoutKg);
        }
    }

    public static EvaluationSummary evaluate(Instance data, int M, Map<String, Integer> K, int nScenarios,
            long baseSeed, WeightConfig cfg) {
        int noStockoutCount = 0;
        double sumStockoutKg = 0.0;

        for (int s = 1; s <= nScenarios; s++) {
            ScenarioResult result = evaluateSingleScenario(data, M, K, baseSeed + s, cfg);
            if (!result.hasStockout) {
                noStockoutCount++;
            }
            sumStockoutKg += result.totalStockoutKg;
        }

        double noStockoutPercentage = (double) noStockoutCount / nScenarios * 100.0;
        double avgStockoutKg = sumStockoutKg / nScenarios;

        return new EvaluationSummary(nScenarios, noStockoutCount, noStockoutPercentage, avgStockoutKg);
    }

    public static ScenarioResult evaluateSingleScenario(Instance data, int M, Map<String, Integer> K, long scenarioSeed,
            WeightConfig cfg) {
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

        double[] vustTarget = computeVustNextDayMaxDemandTarget(vust, cfg.vust());

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
                totalStockoutKg += consumeAndRecordStockout(stockouts, ou.operatingUnitName, "FUEL", dFUEL, t, inv,
                        IDX_FUEL);
                totalStockoutKg += consumeAndRecordStockout(stockouts, ou.operatingUnitName, "AMMO", dAMMO, t, inv,
                        IDX_AMMO);
            }

            // No dispatch on the final day
            if (t == data.timeHorizon) {
                break;
            }

            // 2) Scheduled arrivals decided today
            Map<String, double[]> scheduledDeliveries = new HashMap<>();
            for (OperatingUnit ou : data.operatingUnits) {
                scheduledDeliveries.put(ou.operatingUnitName, new double[] { 0.0, 0.0, 0.0 });
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
                    OperatingUnit chosenOu = selectMostUrgentOU(candidates, ouInv, scheduledDeliveries, blocked,
                            ouMaxDemand, rng, cfg.ou());
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
                            rng,
                            cfg);

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

                    addCclToscheduledDeliveries(scheduledDeliveries.get(chosenOu.operatingUnitName), data.cclTypes,
                            chosenC);

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
                if (choice == null)
                    break;

                FSC f = choice.fsc;
                if (totalCclsAtFsc(f, fscInv) >= f.maxStorageCapCcls)
                    break;

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

            // 5) Apply arrivals to obtain start-of-day inventories for day t+1
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

    private static double[] computeVustNextDayMaxDemandTarget(OperatingUnit vust, TargetWeights w) {
        double targetFW = Math.min(vust.maxFoodWaterKg, vust.dailyFoodWaterKg * MAX_MULT_FW * w.fw());
        double targetFUEL = Math.min(vust.maxFuelKg, vust.dailyFuelKg * MAX_MULT_FUEL * w.fuel());
        double targetAMMO = Math.min(vust.maxAmmoKg, vust.dailyAmmoKg * MAX_MULT_AMMO * w.ammo());

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
            // continue;
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
            Random rng,
            TargetWeights w) {
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

            double ratio = minDemandCoverRatio(inv, add, dmax, w);

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

    private static double minDemandCoverRatio(double[] inv, double[] add, double[] dmax, TargetWeights w) {
        double rFW = (inv[IDX_FW] + add[IDX_FW]) / Math.max(1.0, w.fw() * dmax[IDX_FW]);
        double rFUEL = (inv[IDX_FUEL] + add[IDX_FUEL]) / Math.max(1.0, w.fuel() * dmax[IDX_FUEL]);
        double rAMMO = (inv[IDX_AMMO] + add[IDX_AMMO]) / Math.max(1.0, w.ammo() * dmax[IDX_AMMO]);
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

        return new double[] { dFW, dFUEL, dAMMO };
    }

    private static int selectBestFeasibleCCLTypeAllProducts(
            OperatingUnit ou,
            Map<String, int[]> fscInvByOuType,
            double[] inv,
            double[] add,
            List<CCLpackage> cclTypes,
            double[] dmax,
            Random rng,
            WeightConfig cfg) {
        int[] stock = fscInvByOuType.get(ou.ouType);
        if (stock == null)
            return -1;

        int bestC = -1;
        double bestScore = -1.0;

        int ties = 0;

        for (CCLpackage ccl : cclTypes) {
            int c = ccl.type;
            if (stock[c - 1] <= 0)
                continue;

            if (!fitsCapacity(ou, inv, add, ccl))
                continue;

            // NEW: score = reduction in normalized deficits to target (order-up-to target)
            double score = postDeliveryDeficitReductionScore(ou, inv, add, ccl, dmax, cfg.ou());

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
            double[] dmax,
            TargetWeights w) {
        double targetFW = Math.min(ou.maxFoodWaterKg, Math.max(0.0, w.fw() * dmax[IDX_FW]));
        double targetFUEL = Math.min(ou.maxFuelKg, Math.max(0.0, w.fuel() * dmax[IDX_FUEL]));
        double targetAMMO = Math.min(ou.maxAmmoKg, Math.max(0.0, w.ammo() * dmax[IDX_AMMO]));

        double before = normalizedDeficit(inv[IDX_FW] + add[IDX_FW], targetFW)
                + normalizedDeficit(inv[IDX_FUEL] + add[IDX_FUEL], targetFUEL)
                + normalizedDeficit(inv[IDX_AMMO] + add[IDX_AMMO], targetAMMO);

        double after = normalizedDeficit(inv[IDX_FW] + add[IDX_FW] + ccl.foodWaterKg, targetFW)
                + normalizedDeficit(inv[IDX_FUEL] + add[IDX_FUEL] + ccl.fuelKg, targetFUEL)
                + normalizedDeficit(inv[IDX_AMMO] + add[IDX_AMMO] + ccl.ammoKg, targetAMMO);

        double baseScore = before - after;

        double fuelAfter = inv[IDX_FUEL] + add[IDX_FUEL] + ccl.fuelKg;
        double ammoAfter = inv[IDX_AMMO] + add[IDX_AMMO] + ccl.ammoKg;

        double fuelRatio = fuelAfter / Math.max(1.0, ou.maxFuelKg);
        double ammoRatio = ammoAfter / Math.max(1.0, ou.maxAmmoKg);

        double balancePenalty = Math.abs(fuelRatio - ammoRatio);

        return baseScore - BALANCE_PENALTY_LAMBDA * balancePenalty;
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
            for (int x : arr)
                total += x;
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
            if (totalCclsAtFsc(fsc, fscInv) >= fsc.maxStorageCapCcls)
                continue;

            Map<String, int[]> curByType = fscInv.get(fsc.FSCname);
            if (curByType == null)
                continue;

            for (Map.Entry<String, int[]> initEntry : fsc.initialStorageLevels.entrySet()) {
                String ouType = initEntry.getKey();
                if (ouType.equals("VUST"))
                    continue;

                int[] initArr = initEntry.getValue();
                int[] curArr = curByType.get(ouType);
                if (curArr == null)
                    continue;

                int L = Math.min(initArr.length, curArr.length);

                for (int idx = 0; idx < L; idx++) {
                    int init = initArr[idx];
                    if (init <= 0)
                        continue;

                    int cur = curArr[idx];
                    double ratio = (double) cur / init;

                    int cclType = idx + 1;

                    if (ratio + EPS < bestRatio) {
                        bestRatio = ratio;
                        best = new RefillChoice(fsc, ouType, cclType);
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

    // --- Grid search on target level weights ---

    public static final class GridSearchResult {
        public final WeightConfig bestCfg;
        public final EvaluationSummary bestSummary;

        public GridSearchResult(WeightConfig bestCfg, EvaluationSummary bestSummary) {
            this.bestCfg = bestCfg;
            this.bestSummary = bestSummary;
        }
    }

    public static GridSearchResult gridSearchOuWeights(
            Instance data,
            int M,
            Map<String, Integer> K,
            int nTrainScenarios,
            long baseSeedTrain,
            TargetWeights fixedVust,
            double[] ouFW, double[] ouFUEL, double[] ouAMMO) {
        WeightConfig bestCfg = null;
        EvaluationSummary best = null;

        for (double aFW : ouFW)
            for (double aFU : ouFUEL)
                for (double aAM : ouAMMO) {
                    WeightConfig cfg = new WeightConfig(
                            new TargetWeights(aFW, aFU, aAM),
                            fixedVust);

                    EvaluationSummary s = evaluate(data, M, K, nTrainScenarios, baseSeedTrain, cfg);

                    if (best == null
                            || s.scenariosWithoutStockout > best.scenariosWithoutStockout
                            || (s.scenariosWithoutStockout == best.scenariosWithoutStockout
                                    && s.avgTotalStockoutKg + 1e-9 < best.avgTotalStockoutKg)) {
                        best = s;
                        bestCfg = cfg;
                    }
                }

        return new GridSearchResult(bestCfg, best);
    }

    public static GridSearchResult gridSearchVustWeights(
            Instance data,
            int M,
            Map<String, Integer> K,
            int nTrainScenarios,
            long baseSeedTrain,
            TargetWeights fixedOu,
            double[] vFW, double[] vFUEL, double[] vAMMO) {
        WeightConfig bestCfg = null;
        EvaluationSummary best = null;

        for (double v1 : vFW)
            for (double v2 : vFUEL)
                for (double v3 : vAMMO) {
                    WeightConfig cfg = new WeightConfig(
                            fixedOu,
                            new TargetWeights(v1, v2, v3));

                    EvaluationSummary s = evaluate(data, M, K, nTrainScenarios, baseSeedTrain, cfg);

                    if (best == null
                            || s.scenariosWithoutStockout > best.scenariosWithoutStockout
                            || (s.scenariosWithoutStockout == best.scenariosWithoutStockout
                                    && s.avgTotalStockoutKg + 1e-9 < best.avgTotalStockoutKg)) {
                        best = s;
                        bestCfg = cfg;
                    }
                }

        return new GridSearchResult(bestCfg, best);
    }

    public static final class TuningResult {
        public final WeightConfig bestCfg;
        public final EvaluationSummary bestOuSummary;
        public final EvaluationSummary bestVustSummary;

        public TuningResult(WeightConfig bestCfg, EvaluationSummary bestOuSummary, EvaluationSummary bestVustSummary) {
            this.bestCfg = bestCfg;
            this.bestOuSummary = bestOuSummary;
            this.bestVustSummary = bestVustSummary;
        }
    }

    public static TuningResult tuneWeights(
            Instance data,
            int M,
            Map<String, Integer> K,
            int nTrainScenarios,
            long baseSeedTrain,
            double lb,
            double ub,
            double step,
            TargetWeights defaultVustWeights) {
        double[] grid = buildGrid(lb, ub, step);

        // Phase 1: tune OU weights
        int totalPhase = grid.length * grid.length * grid.length;
        long start1 = System.currentTimeMillis();
        int counter = 0;

        WeightConfig bestCfgOU = null;
        EvaluationSummary bestSumOU = null;

        for (double aFW : grid) {
            for (double aFUEL : grid) {
                for (double aAMMO : grid) {
                    counter++;

                    WeightConfig cfg = new WeightConfig(
                            new TargetWeights(aFW, aFUEL, aAMMO),
                            defaultVustWeights);

                    EvaluationSummary s = evaluate(data, M, K, nTrainScenarios, baseSeedTrain, cfg);

                    if (isBetter(s, bestSumOU)) {
                        bestSumOU = s;
                        bestCfgOU = cfg;
                    }

                    if (counter % 10 == 0 || counter == totalPhase) {
                        printProgressBar("Phase 1 (OU)", counter, totalPhase, start1);
                    }
                }
            }
        }

        System.out.println("Phase 1 best OU weights: " + bestCfgOU.ou());

        // Phase 2: tune VUST weights
        long start2 = System.currentTimeMillis();
        counter = 0;

        WeightConfig bestCfgV = null;
        EvaluationSummary bestSumV = null;

        for (double vFW : grid) {
            for (double vFUEL : grid) {
                for (double vAMMO : grid) {
                    counter++;

                    WeightConfig cfg = new WeightConfig(
                            bestCfgOU.ou(),
                            new TargetWeights(vFW, vFUEL, vAMMO));

                    EvaluationSummary s = evaluate(data, M, K, nTrainScenarios, baseSeedTrain, cfg);

                    if (isBetter(s, bestSumV)) {
                        bestSumV = s;
                        bestCfgV = cfg;
                    }

                    if (counter % 10 == 0 || counter == totalPhase) {
                        printProgressBar("Phase 2 (VUST)", counter, totalPhase, start2);
                    }
                }
            }
        }

        System.out.println("Phase 2 best VUST weights: " + bestCfgV.vust());
        System.out.println("Phase 2 summary: " + bestSumV);

        WeightConfig bestCfg = new WeightConfig(bestCfgOU.ou(), bestCfgV.vust());
        System.out.println("FINAL best config: OU=" + bestCfg.ou() + " | VUST=" + bestCfg.vust());

        return new TuningResult(bestCfg, bestSumOU, bestSumV);
    }

    private static boolean isBetter(EvaluationSummary cand, EvaluationSummary best) {
        if (best == null)
            return true;

        if (cand.scenariosWithoutStockout > best.scenariosWithoutStockout)
            return true;
        if (cand.scenariosWithoutStockout < best.scenariosWithoutStockout)
            return false;

        return cand.avgTotalStockoutKg + 1e-9 < best.avgTotalStockoutKg;
    }

    private static double[] buildGrid(double lb, double ub, double step) {
        int n = (int) Math.floor((ub - lb) / step + 1e-12) + 1;
        double[] grid = new double[n];

        for (int i = 0; i < n; i++) {
            grid[i] = lb + i * step;
            grid[i] = Math.round(grid[i] * 1000.0) / 1000.0;
        }
        return grid;
    }

    private static void printProgressBar(String label, int current, int total, long startTimeMs) {
        int barWidth = 40;

        double progress = (double) current / total;
        int filled = (int) (barWidth * progress);

        long elapsed = System.currentTimeMillis() - startTimeMs;
        double avgTimePerStep = (current == 0) ? 0.0 : (double) elapsed / current;
        long eta = (long) (avgTimePerStep * (total - current));

        String bar = "[" + "=".repeat(filled) + " ".repeat(barWidth - filled) + "]";
        System.out.printf(
                "\r%s %s %3d%% | %d/%d | Elapsed: %.1fs | ETA: %.1fs",
                label,
                bar,
                (int) (progress * 100),
                current,
                total,
                elapsed / 1000.0,
                eta / 1000.0);

        if (current == total)
            System.out.println();
    }

    // --- Grid search on new ccl composition ---

    public static final class CCLGridSearchResult {
        public final newCCLComposition bestComp;
        public final EvaluationSummary bestSummary;

        public CCLGridSearchResult(newCCLComposition bestComp, EvaluationSummary bestSummary) {
            this.bestComp = bestComp;
            this.bestSummary = bestSummary;
        }
    }

    public static CCLGridSearchResult gridSearchCCL(
            int M,
            Map<String, Integer> K,
            int nScenarios,
            long baseSeed,
            WeightConfig cfg,
            int stepKg) {
        final int TOTAL = 10000;

        int units = TOTAL / stepKg;
        int totalConfigs = (units + 2) * (units + 1) / 2;

        long start = System.currentTimeMillis();
        int counter = 0;

        newCCLComposition bestComp = null;
        EvaluationSummary best = null;

        for (int fwU = 0; fwU <= units; fwU++) {
            int fw = fwU * stepKg;

            for (int fuelU = 0; fuelU <= units - fwU; fuelU++) {
                int fuel = fuelU * stepKg;

                int ammo = TOTAL - fw - fuel;

                Instance variant = InstanceCreator.createFDInstanceExtraType(fw, fuel, ammo).get(0);
                EvaluationSummary s = evaluate(variant, M, K, nScenarios, baseSeed, cfg);

                if (best == null
                        || s.scenariosWithoutStockout > best.scenariosWithoutStockout
                        || (s.scenariosWithoutStockout == best.scenariosWithoutStockout
                                && s.avgTotalStockoutKg + 1e-9 < best.avgTotalStockoutKg)) {
                    best = s;
                    bestComp = new newCCLComposition(fw, fuel, ammo);
                }

                counter++;
                if (counter % 5 == 0 || counter == totalConfigs) {
                    printProgressBar("CCL4 grid", counter, totalConfigs, start);
                }
            }
        }

        System.out.println("Best CCL4: " + bestComp + " | " + best);
        return new CCLGridSearchResult(bestComp, best);
    }
}
