package Stochastic;

import DataUtils.InstanceCreator;
import Objects.CCLpackage;
import Objects.FSC;
import Objects.Instance;
import Objects.OperatingUnit;

import com.gurobi.gurobi.*;

import java.util.*;

/**
 * Stochastic fleet sizing via Monte Carlo scenarios.
 * - Sample daily OU demands for FW/FUEL/AMMO for t=1..T.
 * - Convert each (OU, day) demand triple into #CCLs using a covering ILP (min #trucks, with 1 truck = 1 CCL).
 * - Aggregate those required CCL shipments into daily workloads for MSC -> VUST & FSC -> OUs (per FSC)
 * - Raw fleet sizing: for MSC->VUST and each FSC->OU, take the empirical service-level quantile of the scenario peak daily workload.
 *
 * Inventory corrections:
 *  OU initial full inventory credit:
 *     Convert each OU maximum inventory (kg) into "full CCLs" using the same ILP.
 *     Since the horizon is T days and the final day does not require ending full,
 *     we treat approximately 1/T of that full inventory as a per-day credit.
 *     - VUST credit reduces MSC->VUST fleet.
 *     - OU credits aggregated per FSC reduce FSC->OU fleet per FSC.
 *  FSC initial stock credit:
 *  MSC -> FSC pooled estimate: computed from the corrected FSC->OU fleets (pooled), i.e. MSC replenishes what FSCs need to ship:
 *     FSCs start with initial storage already expressed in CCL units.
 *     We sum the initial CCLs across FSCs and divide by T to get a per-day credit
 *     that reduces the pooled MSC->FSC fleet estimate.
 *
 *  Aggregate daily workloads for: MSC -> VUST, FSC -> OUs (per FSC), MSC -> FSC (pooled estimate based on total FSC outbound workload)
 */
public final class StochasticFleetSizerCorrected {

    private StochasticFleetSizerCorrected() {}

    /**
     * Estimates fleet sizes using Monte Carlo sampling + per-OU/day covering ILP.
     *
     * @param instance     problem instance (OUs, FSCs, CCL types, time horizon T)
     * @param numScenarios number of Monte Carlo scenarios to sample (S)
     * @param serviceLevel target service level in (0,1], e.g. 0.95
     * @return fleet-sizing estimates packed in FleetSizingResult
     * @throws GRBException if Gurobi fails when solving per-OU/day ILPs
     */
    public static FleetSizingResult estimateFleetSizes(Instance instance,
                                                       int numScenarios,
                                                       double serviceLevel,
                                                       long baseSeed) throws GRBException {

        if (numScenarios <= 0) throw new IllegalArgumentException("numScenarios must be > 0");
        if (serviceLevel <= 0.0 || serviceLevel > 1.0)
            throw new IllegalArgumentException("serviceLevel must be in (0,1]");

        final int T = instance.timeHorizon;
        final List<CCLpackage> ccls = instance.cclTypes;

        // FSC names (for consistent indexing)
        List<String> fscNames = new ArrayList<>();
        for (FSC f : instance.FSCs) fscNames.add(f.FSCname);

        // Store scenario peak samples:
        // - per FSC: peak daily outbound workload FSC->OUs
        Map<String, List<Integer>> peakSamplesPerFsc = new HashMap<>();
        for (String w : fscNames) peakSamplesPerFsc.put(w, new ArrayList<>(numScenarios));

        // - MSC->VUST: peak daily workload
        List<Integer> peakSamplesMSCtoVUST = new ArrayList<>(numScenarios);

        // Build one reusable ILP solver (also used for inventory->CCL conversions)
        try (PerOuDayCclIlpSolver ilp = new PerOuDayCclIlpSolver(ccls, false)) {

            // Compute the correction factor
            InventoryCredits credits = computeInventoryCredits(instance, ilp);

            // Scenario loop
            for (int s = 0; s < numScenarios; s++) {
                Sampling sampler = new Sampling(baseSeed + s); // TODO: base seed

                // Daily FSC workloads (FSC->OU)
                Map<String, int[]> dailyWorkloadFsc = new HashMap<>();
                for (String w : fscNames) dailyWorkloadFsc.put(w, new int[T]);

                // Daily MSC->VUST workload
                int[] dailyWorkloadVust = new int[T];

                // Loop OUs
                for (OperatingUnit ou : instance.operatingUnits) {
                    String ouName = ou.operatingUnitName;

                    // Sample realized daily demand arrays (length T)
                    double[] dFW = sampler.stochasticFW((int) Math.round(ou.dailyFoodWaterKg), instance.timeHorizon);
                    double[] dFUEL = sampler.stochasticFUEL((int) Math.round(ou.dailyFuelKg), instance.timeHorizon);
                    double[] dAMMO = sampler.stochasticAMMO((int) Math.round(ou.dailyAmmoKg), instance.timeHorizon);

                    for (int t = 0; t < T; t++) {
                        // Convert demand to minimum #CCLs (min trucks)
                        int shipments = ilp.minCclsToCover(dFW[t], dFUEL[t], dAMMO[t]);

                        if ("VUST".equals(ouName)) {
                            dailyWorkloadVust[t] += shipments;
                        } else {
                            String w = ou.source;
                            int[] arr = dailyWorkloadFsc.computeIfAbsent(w, __ -> new int[T]);
                            arr[t] += shipments;
                        }
                    }
                }

                // Peaks within scenario
                peakSamplesMSCtoVUST.add(maxOverDays(dailyWorkloadVust));

                for (Map.Entry<String, int[]> e : dailyWorkloadFsc.entrySet()) {
                    peakSamplesPerFsc
                            .computeIfAbsent(e.getKey(), __ -> new ArrayList<>(numScenarios))
                            .add(maxOverDays(e.getValue()));
                }
            }

            // Quantiles over raw number of trucks
            // Raw MSC->VUST comes from scenario peak quantiles
            int raw_M_VUST = empiricalQuantile(peakSamplesMSCtoVUST, serviceLevel);

            // Raw FSC fleets (per FSC) come from scenario peak quantiles
            Map<String, Integer> raw_K_FSC = new HashMap<>();
            for (Map.Entry<String, List<Integer>> e : peakSamplesPerFsc.entrySet()) {
                int q = e.getValue().isEmpty() ? 0 : empiricalQuantile(e.getValue(), serviceLevel);
                raw_K_FSC.put(e.getKey(), q);
            }

            // Apply inventory correction

            // 1) Correct FSC fleets first (FSC->OU) using OU inventory credits
            Map<String, Integer> corr_K_FSC = new HashMap<>();
            for (String w : fscNames) {
                int rawKw = raw_K_FSC.getOrDefault(w, 0);
                int creditKw = credits.ouCreditPerDayByFsc.getOrDefault(w, 0);
                double OU_CREDIT_FACTOR = 0.95;
                int scaledCreditKw = (int) Math.floor(OU_CREDIT_FACTOR * creditKw);
                corr_K_FSC.put(w, Math.max(0, rawKw - scaledCreditKw));
            }

            // 2) Compute MSC->FSC from corrected FSC outbound needs (pooled)
            int raw_M_MSCtoFSC_fromCorrectedFsc = sumMapValues(corr_K_FSC);

            // 3) Apply FSC initial stock credit to MSC->FSC
            int corr_M_MSCtoFSC = Math.max(0, raw_M_MSCtoFSC_fromCorrectedFsc - credits.fscStockCreditPerDay);

            // 4) Apply VUST credit
            int corr_M_VUST = Math.max(0, raw_M_VUST - credits.vustCreditPerDay);

            // Total objective = (MSC->VUST) + (MSC->FSC pooled) + sum_w (FSC fleets)
            int total = corr_M_VUST + corr_M_MSCtoFSC + sumMapValues(corr_K_FSC);

            return new FleetSizingResult(
                    total,
                    corr_M_VUST,
                    corr_M_MSCtoFSC,
                    corr_K_FSC
            );
        }
    }

    /**
     * Holds the per-day credits derived from initial inventories.
     * Credits are expressed in "trucks per day" (= CCLs per day).
     */
    private static final class InventoryCredits {
        final int vustCreditPerDay;
        final Map<String, Integer> ouCreditPerDayByFsc;
        final int fscStockCreditPerDay;

        InventoryCredits(int vustCreditPerDay,
                         Map<String, Integer> ouCreditPerDayByFsc,
                         int fscStockCreditPerDay) {
            this.vustCreditPerDay = vustCreditPerDay;
            this.ouCreditPerDayByFsc = ouCreditPerDayByFsc;
            this.fscStockCreditPerDay = fscStockCreditPerDay;
        }
    }

    /**
     * Computes “credit per day” (≈ 1/T) for:
     *  - VUST initial max inventory (in kg) converted to CCLs
     *  - OUs initial max inventories aggregated per FSC
     *  - FSC initial storage already in CCLs
     */
    private static InventoryCredits computeInventoryCredits(Instance instance,
                                                            PerOuDayCclIlpSolver ilp) throws GRBException {
        final int T = instance.timeHorizon;

        // Convert OU full-inventory in kg to CCLs
        int vustFullCcls = 0;
        Map<String, Integer> fullCclsByFsc = new HashMap<>();

        for (OperatingUnit ou : instance.operatingUnits) {
            int fullCcls = ilp.minCclsToCover(ou.maxFoodWaterKg, ou.maxFuelKg, ou.maxAmmoKg);

            if ("VUST".equals(ou.operatingUnitName)) {
                vustFullCcls += fullCcls;
            } else {
                String w = ou.source;
                fullCclsByFsc.put(w, fullCclsByFsc.getOrDefault(w, 0) + fullCcls);
            }
        }

        int vustCreditPerDay = roundDivNonNegative(vustFullCcls, T);

        Map<String, Integer> ouCreditPerDayByFsc = new HashMap<>();
        for (Map.Entry<String, Integer> e : fullCclsByFsc.entrySet()) {
            ouCreditPerDayByFsc.put(e.getKey(), roundDivNonNegative(e.getValue(), T));
        }

        // FSC initial storage is already in CCL units
        // initialStorageLevels: Map<ouType, int[3]> where int[3] = (#type1, #type2, #type3)
        int totalInitialFscCcls = 0;
        for (FSC f : instance.FSCs) {
            for (Map.Entry<String, int[]> e : f.initialStorageLevels.entrySet()) {
                String ouType = e.getKey();
                if ("VUST".equals(ouType)) continue;
                int[] vec = e.getValue();
                for (int k : vec) totalInitialFscCcls += k;
            }
        }

        int fscStockCreditPerDay = roundDivNonNegative(totalInitialFscCcls, T);

        return new InventoryCredits(vustCreditPerDay, ouCreditPerDayByFsc, fscStockCreditPerDay);
    }

    /** Round(total/T) as integer, clipping total at >= 0. */
    private static int roundDivNonNegative(int total, int T) {
        if (total <= 0) return 0;
        return (int) Math.floor(total * 1.0 / T);
    }

    /** Returns max element in array (used for peak day). */
    private static int maxOverDays(int[] arr) {
        int m = 0;
        for (int x : arr) m = Math.max(m, x);
        return m;
    }

    /**
     * Empirical quantile using index ceil(q*n)-1.
     *
     * @param samples scenario peak samples
     * @param q       quantile in (0,1]
     * @return empirical quantile value
     */
    private static int empiricalQuantile(List<Integer> samples, double q) {
        if (samples.isEmpty()) return 0;
        List<Integer> copy = new ArrayList<>(samples);
        Collections.sort(copy);
        int n = copy.size();
        int idx = (int) Math.ceil(q * n) - 1;
        if (idx < 0) idx = 0;
        if (idx >= n) idx = n - 1;
        return copy.get(idx);
    }

    /** Sum map values. */
    private static int sumMapValues(Map<String, Integer> map) {
        int s = 0;
        for (int v : map.values()) s += v;
        return s;
    }

    /**
     * Result container
     */
    public static final class FleetSizingResult {

        // Corrected total objective = MSC->VUST + MSC->FSC + sum_w FSC_w.
        public final int totalTrucks;

        // Corrected trucks needed per day for MSC -> VUST.
        public final int trucksMSCtoVUST;

        // Corrected pooled trucks needed per day for MSC -> FSC.
        public final int trucksMSCtoFSC;

        // Corrected trucks per day for each FSC -> its OUs (map FSC name -> trucks).
        public final Map<String, Integer> trucksAtFSC;

        public FleetSizingResult(int totalTrucks,
                                 int trucksMSCtoVUST,
                                 int trucksMSCtoFSC,
                                 Map<String, Integer> trucksAtFSC) {
            this.totalTrucks = totalTrucks;
            this.trucksMSCtoVUST = trucksMSCtoVUST;
            this.trucksMSCtoFSC = trucksMSCtoFSC;
            this.trucksAtFSC = Collections.unmodifiableMap(new HashMap<>(trucksAtFSC));
        }
    }

    /**
     * Reusable Gurobi ILP solver for the per-OU/day "cover demand with minimum CCLs" problem.
     *
     * Decision variables: x[c] = integer number of CCL packages (trucks) of type c to ship.
     * Objective: minimize sum_c x[c]
     * Constraints:
     *   sum_c FW(c)   * x[c] >= fwDemand
     *   sum_c FUEL(c) * x[c] >= fuelDemand
     *   sum_c AMMO(c) * x[c] >= ammoDemand
     *
     * We build the model once, then for each (OU, day) call we only update the RHS values and re-optimize.
     */
    public static final class PerOuDayCclIlpSolver implements AutoCloseable {

        private final GRBEnv env;
        private final GRBModel model;

        private final GRBVar[] x;
        private final GRBConstr fwConstr;
        private final GRBConstr fuelConstr;
        private final GRBConstr ammoConstr;

        public PerOuDayCclIlpSolver(List<CCLpackage> ccls, boolean verbose) throws GRBException {
            this.env = new GRBEnv(true);
            env.set(GRB.IntParam.OutputFlag, verbose ? 1 : 0);
            env.set(GRB.IntParam.LogToConsole, verbose ? 1 : 0);
            env.start();

            this.model = new GRBModel(env);

            int C = ccls.size();
            this.x = new GRBVar[C];

            for (int i = 0; i < C; i++) {
                x[i] = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER, "x_" + ccls.get(i).type);
            }

            GRBLinExpr obj = new GRBLinExpr();
            for (GRBVar v : x) obj.addTerm(1.0, v);
            model.setObjective(obj, GRB.MINIMIZE);

            GRBLinExpr fwLhs = new GRBLinExpr();
            GRBLinExpr fuelLhs = new GRBLinExpr();
            GRBLinExpr ammoLhs = new GRBLinExpr();

            for (int i = 0; i < C; i++) {
                CCLpackage c = ccls.get(i);
                fwLhs.addTerm(c.foodWaterKg, x[i]);
                fuelLhs.addTerm(c.fuelKg, x[i]);
                ammoLhs.addTerm(c.ammoKg, x[i]);
            }

            fwConstr   = model.addConstr(fwLhs,   GRB.GREATER_EQUAL, 0.0, "cover_fw");
            fuelConstr = model.addConstr(fuelLhs, GRB.GREATER_EQUAL, 0.0, "cover_fuel");
            ammoConstr = model.addConstr(ammoLhs, GRB.GREATER_EQUAL, 0.0, "cover_ammo");

            model.update();

            model.set(GRB.IntParam.Presolve, 1);
            model.set(GRB.IntParam.Method, 0);
        }

        public int minCclsToCover(double fwDemand, double fuelDemand, double ammoDemand) throws GRBException {
            fwConstr.set(GRB.DoubleAttr.RHS, Math.max(0.0, fwDemand));
            fuelConstr.set(GRB.DoubleAttr.RHS, Math.max(0.0, fuelDemand));
            ammoConstr.set(GRB.DoubleAttr.RHS, Math.max(0.0, ammoDemand));

            model.optimize();

            int status = model.get(GRB.IntAttr.Status);
            if (status != GRB.Status.OPTIMAL) {
                throw new GRBException("Per-OU/day ILP not optimal. Status=" + status);
            }

            return (int) Math.ceil(model.get(GRB.DoubleAttr.ObjVal));
        }

        @Override
        public void close() {
            try { model.dispose(); } catch (Exception ignored) {}
            try { env.dispose(); } catch (Exception ignored) {}
        }
    }

}
