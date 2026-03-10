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
        Instance fdInstance = InstanceCreator.createFDInstance().get(0);
        Instance dispersedInstance = InstanceCreator.contiguousPartitions().get(41); // Index of the instance with the
                                                                                     // most FSCs and obj. 100
        List<Instance> allInstances = List.of(fdInstance, dispersedInstance);

        // MILP (uncomment to run)
        // List<Result> result = CapacitatedResupplyMILP.solveInstances(allInstances);
        // System.out.println();

        // for (Result res : result) {
        //     System.out.println(res.getTruckVector());
        // }

        // DISPERSED EXPERIMENTS (uncomment to run)
        // runDispersedExperiments();

        // STOCHASTIC EXPERIMENTS (uncomment to run)
        // runStochasticExperiments(fdInstance);

        // PERFECT HINDSIGHT EXPERIMENTS (uncomment to run)
        // runPerfectHindsightExperiments();

        // SENSITIVITY ANALYSIS (uncomment to run)
        runSensitivityAnalysis(allInstances);
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
     * 1) Determine fleet size
     * 2) Tune target level weights
     * 3) Out-of-sample evaluation
     * 4) Tune composition new CCL type
     * 5) Out-of-sample evaluation
     * 
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
        EvaluationSummary oosSummary = EvaluationHeuristic.evaluate(base, M, K, nScenarios, seedOOS, bestCfg,
                List.of(0.0, 0.0, 0.0, 0.0)); // no correlation
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

        var oos2 = EvaluationHeuristic.evaluate(bestInst, M, K, nScenarios, seedOOS, bestCfg,
                List.of(0.0, 0.0, 0.0, 0.0)); // no correlation
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

        System.out.println();
        System.out.println("--- Perfect hindsight: 3 CCL types ---");
        System.out.println("N=" + N + " | seed=" + oosSeed + " | fixed fleet: M=" + M + " K=" + K);

        PerfectHindsight.run(base3, N, oosSeed, M, K);

        Instance base4 = InstanceCreator.createFDInstanceExtraType(2000, 6000, 2000).get(0);

        System.out.println();
        System.out.println("--- Perfect hindsight: 4 CCL types (CCL4=2000/6000/2000) ---");
        System.out.println("N=" + N + " | seed=" + oosSeed + " | fixed fleet: M=" + M + " K=" + K);

        PerfectHindsight.run(base4, N, oosSeed, M, K);
    }

    /**
     * // STILL NEED TO IMPLEMENT AND MAYBE ADJUST THE COMMENTS BELOW
     * 
     * Performs a sensitivity analysis on our previous results. We study the effect
     * of
     * 1) The effect of a smaller fleet size
     * 2) Different demand distributions
     * 3) Correlation between products
     * 4) Correlation between days
     * 5) A longer time horizon
     * 6) Making truck reallocation possible
     */
    private static void runSensitivityAnalysis(List<Instance> instances) {
        // CORRELATION
        System.out.println("SENSITIVITY ANALYSIS : CORRELATION");

        final int M = 79;
        final Map<String, Integer> K = new HashMap<>();
        K.put("FSC_1", 48);
        K.put("FSC_2", 35);

        final int nScenarios = 1000;
        final int seedOOS = 10042; // can be anything

        final EvaluationHeuristic.WeightConfig bestCfg = new EvaluationHeuristic.WeightConfig(
                new EvaluationHeuristic.TargetWeights(1.3, 0.7, 1.3),
                new EvaluationHeuristic.TargetWeights(1.0, 1.0, 1.2));

        final List<List<Double>> correlationsList = List.of(
                List.of(0.0, 0.0, 0.0, 0.0), // no correlation
                List.of(0.5, 0.5, 0.5, 0.5),
                List.of(0.1, 0.1, 0.1, 0.1));

        for (List<Double> correlations : correlationsList) {
            for (Instance instance : instances) {
                EvaluationSummary oosSummary = EvaluationHeuristic.evaluate(instance, M, K, nScenarios, seedOOS, bestCfg, correlations);
                System.out.println(oosSummary);
            }
        }

        // DIFFERENT DEMAND DISTRIBUTIONS
        // Investigate the effect of scaling the mean and standard deviation of the demand
        // distributions independently, using multipliers applied around the base mean of 1.0.
        System.out.println();
        System.out.println("SENSITIVITY ANALYSIS : DEMAND DISTRIBUTIONS (no correlation)");

        final List<Double> demandMultipliers = List.of(0.9, 0.95, 1.0, 1.05, 1.1);

        final String[] instanceLabels = {"FD", "Dispersed"};

        System.out.println("--- Mean sensitivity (stdMult = 1.0) ---");
        for (double mult : demandMultipliers) {
            for (int i = 0; i < instances.size(); i++) {
                EvaluationSummary summary = EvaluationHeuristic.evaluate(
                        instances.get(i), M, K, nScenarios, seedOOS, bestCfg, List.of(0.0, 0.0, 0.0, 0.0), mult, 1.0);
                System.out.println("meanMult=" + mult + " | " + instanceLabels[i] + " | " + summary);
            }
        }

        System.out.println("--- Std sensitivity (meanMult = 1.0) ---");
        for (double mult : demandMultipliers) {
            for (int i = 0; i < instances.size(); i++) {
                EvaluationSummary summary = EvaluationHeuristic.evaluate(
                        instances.get(i), M, K, nScenarios, seedOOS, bestCfg, List.of(0.0, 0.0, 0.0, 0.0), 1.0, mult);
                System.out.println("stdMult=" + mult + " | " + instanceLabels[i] + " | " + summary);
            }
        }
    }
}