import DataUtils.InstanceCreator;
import DataUtils.OutputCreator;
import Deterministic.CapacitatedResupplyMILP;
import Stochastic.*;
import Stochastic.EvaluationHeuristic.EvaluationSummary;
import Objects.*;
import java.util.*;

import com.gurobi.gurobi.GRBException;

import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException {
        Instance base = InstanceCreator.createFDInstance().get(0);

        // FD EXPERIMENTS (uncomment to run)
        // runFDExperiments(base);

        // DISPERSED EXPERIMENTS (uncomment to run)
        // runDispersedExperiments();

        // STOCHASTIC EXPERIMENTS (uncomment to run)
        // runStochasticExperiments(base);

        // PERFECT HINDSIGHT EXPERIMENTS (uncomment to run)
        runPerfectHindsightExperiments();

        // SENSITIVITY ANALYSIS (uncomment to run)

    }

    /**
     * Solves the deterministic MILP for the FD instance.
     * 
     * @param base FD instance
     * @throws IOException if an error occurs
     */
    private static void runFDExperiments(Instance base) throws IOException {
        List<Result> result = CapacitatedResupplyMILP.solveInstances(List.of(base));
    }

    /**
     * Solves the deterministic MILP for all 512 contiguous partitons (Dispersed
     * instances).
     * Writes the output to a CSV file named DispersedConceptSolutions.csv.
     */
    private static void runDispersedExperiments() {
        long t0 = System.currentTimeMillis();
        List<Result> results = CapacitatedResupplyMILP.solveInstances(InstanceCreator.contiguousPartitions());
        long t1 = System.currentTimeMillis();
        long timeSeconds = (t1 - t0) / 1000;
        System.out.println("Runtime (s) : " + timeSeconds);
        new OutputCreator().createCSV(results);
    }

    /**
     * Runs the stochastic experiments. The method follows the steps: 
     *  1) Determine fleet size
     *  2) Tune target level weights
     *  3) Out-of-sample evaluation
     *  4) Tune composition new CCL type
     *  5) Out-of-sample evaluation
     * @param base FD instance
     */
    private static void runStochasticExperiments(Instance base) {
        final int nScenarios = 1000;
        final double serviceLevel = 0.95;

        final long seedSizingTrain = 42000;
        final long seedTuningTrain = 43000;
        final long seedCompTrain = 44000;
        final int seedOOS = 10042;

        System.out.println();
        System.out.println("STEP 1 : STOCHASTIC FLEET SIZING");
        StochasticFleetSizerCorrected.FleetSizingResult fleet;
        try {
            long t0 = System.currentTimeMillis();
            fleet = StochasticFleetSizerCorrected.estimateFleetSizes(base, nScenarios, serviceLevel, seedSizingTrain);
            long t1 = System.currentTimeMillis();
            long timeSeconds = (t1 - t0) / 1000;

            System.out.println("Runtime (s) : " + timeSeconds);
            System.out.println("Total trucks = " + fleet.totalTrucks);
            int mscTrucks = fleet.trucksMSCtoFSC + fleet.trucksMSCtoVUST;
            System.out.println("MSC =" + mscTrucks);
            for (String w : new TreeSet<>(fleet.trucksAtFSC.keySet())) {
                System.out.println("  " + w + " = " + fleet.trucksAtFSC.get(w));
            }
        } catch (GRBException e) {
            throw new RuntimeException();
        }

        final int M = fleet.trucksMSCtoFSC + fleet.trucksMSCtoVUST;
        final Map<String, Integer> K = new HashMap<>(fleet.trucksAtFSC);

        System.out.println();
        System.out.println("STEP 2 : WEIGHT TUNING (train)");

        double lb = 0.5, ub = 1.5, step = 0.1;
        EvaluationHeuristic.TargetWeights defaultVust = new EvaluationHeuristic.TargetWeights(1.0, 1.0, 1.0);

        var tuning = EvaluationHeuristic.tuneWeights(
                base, M, K, nScenarios, (int) seedTuningTrain,
                lb, ub, step, defaultVust);

        System.out.println("BEST CONFIG : OU=" + tuning.bestCfg.ou() + " VUST=" +
                tuning.bestCfg.vust());

        EvaluationHeuristic.WeightConfig bestCfg = tuning.bestCfg;

        System.out.println();
        System.out.println("STEP 3 : OOS EVALUATION 3 CCLs (test)");
        EvaluationSummary oosSummary = EvaluationHeuristic.evaluate(base, M, K, nScenarios, seedOOS, bestCfg);
        System.out.println(oosSummary);

        int stepKg = 1000;
        System.out.println();
        System.out.println("STEP 4 : CCL COMPOSITION GRID SEARCH (train)");

        var compRes = EvaluationHeuristic.gridSearchCCL(
                M, K, nScenarios, (int) seedCompTrain, bestCfg, stepKg);
        System.out.println("Chosen composition: " + compRes.bestComp);
        System.out.println("Train summary: " + compRes.bestSummary);

        Instance bestInst = InstanceCreator.createFDInstanceExtraType(
                compRes.bestComp.fwKg(),
                compRes.bestComp.fuelKg(),
                compRes.bestComp.ammoKg()).get(0);

        System.out.println();
        System.out.println("STEP 5 : OOS EVALUATION 4 CCLs (test)");

        var oos2 = EvaluationHeuristic.evaluate(bestInst, M, K, nScenarios, seedOOS, bestCfg);
        System.out.println(oos2);
    }

    /**
     * Performs perfect hindsight experiments. 
     */
    private static void runPerfectHindsightExperiments() {
        final int N = 1000;
        final int oosSeed = 10042;

        final int M = 79; 
        final Map<String, Integer> K = new HashMap<>();
        K.put("FSC_1", 48);
        K.put("FSC_2", 35);

        Instance base3 = InstanceCreator.createFDInstance().get(0);

        // System.out.println();
        // System.out.println("--- Perfect hindsight: 3 CCL types ---");
        // System.out.println("N=" + N + " | seed=" + oosSeed + " | fixed fleet: M=" + M + " K=" + K);

        // PerfectHindsight.run(base3, N, oosSeed, M, K);

        Instance base4 = InstanceCreator.createFDInstanceExtraType(2000, 6000, 2000).get(0);

        System.out.println();
        System.out.println("--- Perfect hindsight: 4 CCL types (CCL4=2000/6000/2000) ---");
        System.out.println("N=" + N + " | seed=" + oosSeed + " | fixed fleet: M=" + M + " K=" + K);

        PerfectHindsight.run(base4, N, oosSeed, M, K);
    }
}