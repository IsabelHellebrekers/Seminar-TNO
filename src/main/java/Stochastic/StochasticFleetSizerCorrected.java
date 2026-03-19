package Stochastic;

import Objects.CCLPackage;
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
 *
 * @author 621349it Ies Timmerarends
 * @author 612348ih Isabel Hellebrekers
 * @author 631426ls Lena Stiebing
 * @author 661267eb Eeke Bavelaar
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

        if (numScenarios <= 0) {
            throw new IllegalArgumentException("numScenarios must be > 0");
        }
        if (serviceLevel <= 0.0 || serviceLevel > 1.0) {
            throw new IllegalArgumentException("serviceLevel must be in (0,1]");
        }

        final int timeHorizon = instance.getTimeHorizon();
        final List<CCLPackage> ccls = instance.getCclTypes();

        // FSC names (for consistent indexing)
        List<String> fscNames = new ArrayList<>();
        for (FSC f : instance.getFSCs()) { fscNames.add(f.getName()); }

        // Store scenario peak samples:
        // - per FSC: peak daily outbound workload FSC->OUs
        Map<String, List<Integer>> peakSamplesPerFsc = new HashMap<>();
        for (String w : fscNames) { peakSamplesPerFsc.put(w, new ArrayList<>(numScenarios)); }

        // - MSC->VUST: peak daily workload
        List<Integer> peakSamplesMSCtoVUST = new ArrayList<>(numScenarios);

        // Build one reusable ILP solver (also used for inventory->CCL conversions)
        try (PerOuDayCclIlpSolver ilp = new PerOuDayCclIlpSolver(ccls, false)) {

            // Compute the correction factor
            InventoryCredits credits = computeInventoryCredits(instance, ilp);

            // Scenario loop
            for (int s = 0; s < numScenarios; s++) {
                Sampling sampler = new Sampling(baseSeed + s);

                // Daily FSC workloads (FSC->OU)
                Map<String, int[]> dailyWorkloadFsc = new HashMap<>();
                for (String w : fscNames) { dailyWorkloadFsc.put(w, new int[timeHorizon]); }

                // Daily MSC->VUST workload
                int[] dailyWorkloadVust = new int[timeHorizon];

                // Loop OUs
                for (OperatingUnit ou : instance.getOperatingUnits()) {
                    String ouName = ou.getName();

                    // Sample realized daily demand arrays (length T)
                    double[] dFW = sampler.stochasticFW((int) Math.round(ou.getDailyFoodWaterKg()), instance.getTimeHorizon());
                    double[] dFUEL = sampler.stochasticFUEL((int) Math.round(ou.getDailyFuelKg()), instance.getTimeHorizon());
                    double[] dAMMO = sampler.stochasticAMMO((int) Math.round(ou.getDailyAmmoKg()), instance.getTimeHorizon());

                    for (int t = 0; t < timeHorizon; t++) {
                        // Convert demand to minimum #CCLs (min trucks)
                        int shipments = ilp.minCclsToCover(dFW[t], dFUEL[t], dAMMO[t]);

                        if ("VUST".equals(ouName)) {
                            dailyWorkloadVust[t] += shipments;
                        } else {
                            String w = ou.getSource();
                            int[] arr = dailyWorkloadFsc.computeIfAbsent(w, __ -> new int[timeHorizon]);
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
            int rawMscVustTrucks = empiricalQuantile(peakSamplesMSCtoVUST, serviceLevel);

            // Raw FSC fleets (per FSC) come from scenario peak quantiles
            Map<String, Integer> rawFscTrucks = new HashMap<>();
            for (Map.Entry<String, List<Integer>> e : peakSamplesPerFsc.entrySet()) {
                int q = e.getValue().isEmpty() ? 0 : empiricalQuantile(e.getValue(), serviceLevel);
                rawFscTrucks.put(e.getKey(), q);
            }

            // Apply inventory correction

            // 1) Correct FSC fleets first (FSC->OU) using OU inventory credits
            Map<String, Integer> correctedFscTrucks = new HashMap<>();
            for (String w : fscNames) {
                int rawKw = rawFscTrucks.getOrDefault(w, 0);
                int creditKw = credits.ouCreditPerDayByFsc.getOrDefault(w, 0);
                final double ouCreditFactor = 0.95;
                int scaledCreditKw = (int) Math.floor(ouCreditFactor * creditKw);
                correctedFscTrucks.put(w, Math.max(0, rawKw - scaledCreditKw));
            }

            // 2) Compute MSC->FSC from corrected FSC outbound needs (pooled)
            int rawMscToFscTrucks = sumMapValues(correctedFscTrucks);

            // 3) Apply FSC initial stock credit to MSC->FSC
            int correctedMscToFscTrucks = Math.max(0, rawMscToFscTrucks - credits.fscStockCreditPerDay);

            // 4) Apply VUST credit
            int correctedMscVustTrucks = Math.max(0, rawMscVustTrucks - credits.vustCreditPerDay);

            // Total objective = (MSC->VUST) + (MSC->FSC pooled) + sum_w (FSC fleets)
            int total = correctedMscVustTrucks + correctedMscToFscTrucks + sumMapValues(correctedFscTrucks);

            return new FleetSizingResult(
                    total,
                    correctedMscVustTrucks,
                    correctedMscToFscTrucks,
                    correctedFscTrucks
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
     * Computes a "credit per day" (approximately 1/T of starting inventory) for:
     *  - VUST initial max inventory (in kg) converted to CCLs
     *  - OUs initial max inventories aggregated per FSC
     *  - FSC initial storage already expressed in CCL units
     */
    private static InventoryCredits computeInventoryCredits(Instance instance,
                                                            PerOuDayCclIlpSolver ilp) throws GRBException {
        final int timeHorizon = instance.getTimeHorizon();

        // Convert OU full-inventory in kg to CCLs
        int vustFullCcls = 0;
        Map<String, Integer> fullCclsByFsc = new HashMap<>();

        for (OperatingUnit ou : instance.getOperatingUnits()) {
            int fullCcls = ilp.minCclsToCover(ou.getMaxFoodWaterKg(), ou.getMaxFuelKg(), ou.getMaxAmmoKg());

            if ("VUST".equals(ou.getName())) {
                vustFullCcls += fullCcls;
            } else {
                String w = ou.getSource();
                fullCclsByFsc.put(w, fullCclsByFsc.getOrDefault(w, 0) + fullCcls);
            }
        }

        int vustCreditPerDay = roundDivNonNegative(vustFullCcls, timeHorizon);

        Map<String, Integer> ouCreditPerDayByFsc = new HashMap<>();
        for (Map.Entry<String, Integer> e : fullCclsByFsc.entrySet()) {
            ouCreditPerDayByFsc.put(e.getKey(), roundDivNonNegative(e.getValue(), timeHorizon));
        }

        // FSC initial storage is already in CCL units
        // initialStorageLevels: Map<ouType, int[3]> where int[3] = (#type1, #type2, #type3)
        int totalInitialFscCcls = 0;
        for (FSC f : instance.getFSCs()) {
            for (Map.Entry<String, int[]> e : f.getInitialStorageLevels().entrySet()) {
                String ouType = e.getKey();
                if ("VUST".equals(ouType)) { continue; }
                int[] vec = e.getValue();
                for (int k : vec) { totalInitialFscCcls += k; }
            }
        }

        int fscStockCreditPerDay = roundDivNonNegative(totalInitialFscCcls, timeHorizon);

        return new InventoryCredits(vustCreditPerDay, ouCreditPerDayByFsc, fscStockCreditPerDay);
    }

    /** Round(total/horizon) as integer, clipping total at >= 0. */
    private static int roundDivNonNegative(int total, int horizon) {
        if (total <= 0) {
            return 0;
        }
        return (int) Math.floor(total * 1.0 / horizon);
    }

    /** Returns max element in array (used for peak day). */
    private static int maxOverDays(int[] arr) {
        int m = 0;
        for (int x : arr) { m = Math.max(m, x); }
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
        if (samples.isEmpty()) {
            return 0;
        }
        List<Integer> copy = new ArrayList<>(samples);
        Collections.sort(copy);
        int n = copy.size();
        int idx = (int) Math.ceil(q * n) - 1;
        if (idx < 0) {
            idx = 0;
        }
        if (idx >= n) {
            idx = n - 1;
        }
        return copy.get(idx);
    }

    /** Sum map values. */
    private static int sumMapValues(Map<String, Integer> map) {
        int s = 0;
        for (int v : map.values()) { s += v; }
        return s;
    }

    /**
     * Holds the corrected fleet-sizing estimates returned by
     * {@link StochasticFleetSizerCorrected#estimateFleetSizes}.
     */
    public static final class FleetSizingResult {

        // Corrected total objective = MSC->VUST + MSC->FSC + sum_w FSC_w.
        private final int totalTrucks;

        // Corrected trucks needed per day for MSC -> VUST.
        private final int trucksMSCtoVUST;

        // Corrected pooled trucks needed per day for MSC -> FSC.
        private final int trucksMSCtoFSC;

        // Corrected trucks per day for each FSC -> its OUs (map FSC name -> trucks).
        private final Map<String, Integer> trucksAtFSC;

        /**
         * Stores the corrected fleet-sizing estimates.
         *
         * @param totalTrucks     total fleet size across all routes after inventory corrections
         * @param trucksMSCtoVUST corrected daily truck requirement for the MSC-to-VUST route
         * @param trucksMSCtoFSC  corrected pooled daily truck requirement for MSC-to-FSC resupply
         * @param trucksAtFSC     corrected daily truck requirement per FSC for FSC-to-OU deliveries
         */
        public FleetSizingResult(int totalTrucks,
                                 int trucksMSCtoVUST,
                                 int trucksMSCtoFSC,
                                 Map<String, Integer> trucksAtFSC) {
            this.totalTrucks = totalTrucks;
            this.trucksMSCtoVUST = trucksMSCtoVUST;
            this.trucksMSCtoFSC = trucksMSCtoFSC;
            this.trucksAtFSC = Collections.unmodifiableMap(new HashMap<>(trucksAtFSC));
        }

        /**
         * Returns the corrected total number of trucks.
         *
         * @return total truck count
         */
        public int getTotalTrucks() {
            return totalTrucks;
        }

        /**
         * Returns the corrected number of trucks for the MSC to VUST route.
         *
         * @return MSC-to-VUST truck count
         */
        public int getTrucksMSCtoVUST() {
            return trucksMSCtoVUST;
        }

        /**
         * Returns the corrected pooled number of trucks for MSC to FSC routes.
         *
         * @return MSC-to-FSC pooled truck count
         */
        public int getTrucksMSCtoFSC() {
            return trucksMSCtoFSC;
        }

        /**
         * Returns the corrected truck counts per FSC, keyed by FSC name.
         *
         * @return unmodifiable map of FSC name to truck count
         */
        public Map<String, Integer> getTrucksAtFSC() {
            return trucksAtFSC;
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

        /**
         * Builds and initialises the per-OU/day CCL covering ILP.
         *
         * @param ccls    list of available CCL package types
         * @param verbose true to enable Gurobi logging, false to suppress it
         * @throws GRBException if the Gurobi model cannot be built
         */
        public PerOuDayCclIlpSolver(List<CCLPackage> ccls, boolean verbose) throws GRBException {
            this.env = new GRBEnv(true);
            env.set(GRB.IntParam.OutputFlag, verbose ? 1 : 0);
            env.set(GRB.IntParam.LogToConsole, verbose ? 1 : 0);
            env.start();

            this.model = new GRBModel(env);

            int numCclTypes = ccls.size();
            this.x = new GRBVar[numCclTypes];

            for (int i = 0; i < numCclTypes; i++) {
                x[i] = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER, "x_" + ccls.get(i).getType());
            }

            GRBLinExpr obj = new GRBLinExpr();
            for (GRBVar v : x) { obj.addTerm(1.0, v); }
            model.setObjective(obj, GRB.MINIMIZE);

            GRBLinExpr fwLhs = new GRBLinExpr();
            GRBLinExpr fuelLhs = new GRBLinExpr();
            GRBLinExpr ammoLhs = new GRBLinExpr();

            for (int i = 0; i < numCclTypes; i++) {
                CCLPackage c = ccls.get(i);
                fwLhs.addTerm(c.getFoodWaterKg(), x[i]);
                fuelLhs.addTerm(c.getFuelKg(), x[i]);
                ammoLhs.addTerm(c.getAmmoKg(), x[i]);
            }

            fwConstr   = model.addConstr(fwLhs,   GRB.GREATER_EQUAL, 0.0, "cover_fw");
            fuelConstr = model.addConstr(fuelLhs, GRB.GREATER_EQUAL, 0.0, "cover_fuel");
            ammoConstr = model.addConstr(ammoLhs, GRB.GREATER_EQUAL, 0.0, "cover_ammo");

            model.update();

            model.set(GRB.IntParam.Presolve, 1);
            model.set(GRB.IntParam.Method, 0);
        }

        /**
         * Solves the covering ILP for the given demand triple and returns the minimum
         * number of CCL packages needed.
         *
         * @param fwDemand   food/water demand to cover (kg)
         * @param fuelDemand fuel demand to cover (kg)
         * @param ammoDemand ammunition demand to cover (kg)
         * @return minimum number of CCL packages needed to cover the demand
         * @throws GRBException if the ILP is not optimal
         */
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
        public void close() throws GRBException{
            model.dispose();
            env.dispose();
        }
    }

}
