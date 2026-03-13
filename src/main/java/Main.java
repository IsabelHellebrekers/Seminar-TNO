import DataUtils.InstanceCreator;
import DataUtils.OutputCreator;
import Deterministic.CapacitatedResupplyMILP;
import Stochastic.*;
import Stochastic.EvaluationHeuristic.EvaluationSummary;
import Objects.*;
import java.util.*;
import java.io.*;

import com.gurobi.gurobi.GRBException;

import Analysis.*;

import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException {
        Instance fdInstance = InstanceCreator.createFDInstance().get(0);
        Instance dispersedInstance = InstanceCreator.contiguousPartitions().get(41);

        // MILP (uncomment to run)
        // List<Result> result = CapacitatedResupplyMILP.solveInstances(allInstances);
        // System.out.println();

        // for (Result res : result) {
        // System.out.println(res.getTruckVector());
        // }

        // DISPERSED EXPERIMENTS (uncomment to run)
        // runDispersedExperiments();

        // STOCHASTIC EXPERIMENTS (uncomment to run)
        runStochasticExperiments(fdInstance);

        // PERFECT HINDSIGHT EXPERIMENTS (uncomment to run)
        // runPerfectHindsightExperiments();

        // SENSITIVITY ANALYSIS (uncomment to run)
        // List<InstanceConfig> sensitivityConfigs = buildSensitivityConfigs(fdInstance, dispersedInstance);
        // runCorrelationAnalysis(sensitivityConfigs);
        // runDemandDistributionAnalysis(sensitivityConfigs);

        // EXTENDED TIME HORIZON ANALYSIS
        // runExtendedTimeHorizon();

        // VEHICLE BREAKDOWN ANALYSIS
        // runVehicleBreakdownAnalysis();
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
    private static void runStochasticExperimentsOld(Instance base) {
        final int nScenarios = 1000;
        final int nOOS = 10000;
        final double serviceLevel = 0.95;

        final long seedSizingTrain = 42000;
        final long seedTuningTrain = 43000;
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
        EvaluationSummary oosSummary = EvaluationHeuristic.evaluate(base, M, K, nOOS, seedOOS, bestCfg,
                List.of(0.0, 0.0, 0.0, 0.0));
        System.out.println(oosSummary);

        System.out.println();
        System.out.println("STEP 4 : TUNE WEIGHTS AGAIN BASED ON NEW CCL TYPE (train)");

        Instance newInstance = InstanceCreator.createFDInstanceExtraType(2000, 7000, 1000).get(0);

        var tuning2 = EvaluationHeuristic.tuneWeights(
                newInstance, M, K, nScenarios, (int) seedTuningTrain,
                lb, ub, step, defaultVust);

        System.out.println("BEST CONFIG : OU=" + tuning2.bestCfg.ou() + " VUST=" +
                tuning2.bestCfg.vust());

        EvaluationHeuristic.WeightConfig bestCfg2 = tuning2.bestCfg;

        System.out.println();
        System.out.println("STEP 5 : OOS EVALUATION 4 CCLs (test)");

        var oos2 = EvaluationHeuristic.evaluate(newInstance, M, K, nOOS, seedOOS, bestCfg2,
                List.of(0.0, 0.0, 0.0, 0.0));
        System.out.println(oos2);
    }

    private static void runStochasticExperiments(Instance base) {
        final int nScenarios = 1000;
        final int nOOS = 10000;
        final double serviceLevel = 0.95;

        final long seedSizingTrain = 42000;
        final long seedTuningTrain = 43000;
        final int seedOOS = 10042;

        final String outputFile = "stochastic_experiments_log.txt";

        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(outputFile, true)))) {

            log(writer, "");
            log(writer, "STEP 1 : STOCHASTIC FLEET SIZING");

            StochasticFleetSizerCorrected.FleetSizingResult fleet;
            try {
                long t0 = System.currentTimeMillis();
                fleet = StochasticFleetSizerCorrected.estimateFleetSizes(
                        base, nScenarios, serviceLevel, seedSizingTrain);
                long t1 = System.currentTimeMillis();
                long timeSeconds = (t1 - t0) / 1000;

                log(writer, "Runtime (s) : " + timeSeconds);
                log(writer, "Total trucks = " + fleet.totalTrucks);

                int mscTrucks = fleet.trucksMSCtoFSC + fleet.trucksMSCtoVUST;
                log(writer, "MSC = " + mscTrucks);

                for (String w : new TreeSet<>(fleet.trucksAtFSC.keySet())) {
                    log(writer, "  " + w + " = " + fleet.trucksAtFSC.get(w));
                }
            } catch (GRBException e) {
                log(writer, "ERROR in STEP 1: " + e.getMessage());
                throw new RuntimeException(e);
            }

            final int M = fleet.trucksMSCtoFSC + fleet.trucksMSCtoVUST;
            final Map<String, Integer> K = new HashMap<>(fleet.trucksAtFSC);

            log(writer, "");
            log(writer, "STEP 2 : WEIGHT TUNING (train)");

            double lb = 0.5, ub = 1.5, step = 0.1;
            EvaluationHeuristic.TargetWeights defaultVust =
                    new EvaluationHeuristic.TargetWeights(1.0, 1.0, 1.0);

            var tuning = EvaluationHeuristic.tuneWeights(
                    base, M, K, nScenarios, (int) seedTuningTrain,
                    lb, ub, step, defaultVust);

            log(writer, "BEST CONFIG : OU = " + tuning.bestCfg.ou() +
                    " | VUST = " + tuning.bestCfg.vust());

            EvaluationHeuristic.WeightConfig bestCfg = tuning.bestCfg;

            log(writer, "");
            log(writer, "STEP 3 : OOS EVALUATION 3 CCLs (test)");

            EvaluationSummary oosSummary = EvaluationHeuristic.evaluate(
                    base, M, K, nOOS, seedOOS, bestCfg,
                    List.of(0.0, 0.0, 0.0, 0.0));

            log(writer, oosSummary.toString());

            log(writer, "");
            log(writer, "STEP 4 + 5 : LOOP OVER 10 INSTANCES WITH DIFFERENT 4TH CCL COMPOSITIONS");

            List<int[]> compositions = List.of(
                    new int[]{3000, 7000, 0},
                    new int[]{2000, 7000, 1000},
                    new int[]{1000, 7000, 2000},
                    new int[]{0, 7000, 3000},
                    new int[]{2000, 8000, 0},
                    new int[]{1000, 8000, 1000},
                    new int[]{0, 8000, 2000},
                    new int[]{1000, 9000, 0},
                    new int[]{0, 9000, 1000},
                    new int[]{0, 10000, 0}
            );

            List<ExperimentResult> results = new ArrayList<>();

            for (int i = 0; i < compositions.size(); i++) {
                int[] comp = compositions.get(i);

                log(writer, "");
                log(writer, "------------------------------------------------------------");
                log(writer, "INSTANCE " + (i + 1));
                log(writer, "Fourth CCL composition = [" + comp[0] + ", " + comp[1] + ", " + comp[2] + "]");
                log(writer, "------------------------------------------------------------");

                Instance inst = InstanceCreator.createFDInstanceExtraType(
                        comp[0], comp[1], comp[2]).get(0);

                // Same seeds for every instance, exactly as requested
                var tuning4 = EvaluationHeuristic.tuneWeights(
                        inst, M, K, nScenarios, (int) seedTuningTrain,
                        lb, ub, step, defaultVust);

                log(writer, "BEST CONFIG : OU = " + tuning4.bestCfg.ou() +
                        " | VUST = " + tuning4.bestCfg.vust());

                EvaluationHeuristic.WeightConfig bestCfg4 = tuning4.bestCfg;

                EvaluationSummary oos4 = EvaluationHeuristic.evaluate(
                        inst, M, K, nOOS, seedOOS, bestCfg4,
                        List.of(0.0, 0.0, 0.0, 0.0));

                log(writer, "OOS RESULT INSTANCE " + (i + 1) + ":");
                log(writer, oos4.toString());

                results.add(new ExperimentResult(i + 1, comp, bestCfg4, oos4));
            }

            log(writer, "");
            log(writer, "============================================================");
            log(writer, "SUMMARY OVER ALL 10 INSTANCES");
            log(writer, "============================================================");

            for (ExperimentResult r : results) {
                log(writer, "Instance " + r.instanceId());
                log(writer, "  Composition       = [" + r.composition()[0] + ", "
                        + r.composition()[1] + ", " + r.composition()[2] + "]");
                log(writer, "  Best OU weights   = " + r.bestCfg().ou());
                log(writer, "  Best VUST weights = " + r.bestCfg().vust());
                log(writer, "  OOS summary       = " + r.oosSummary());
                log(writer, "");
            }

            log(writer, "Run finished successfully.");
            writer.flush();

        } catch (IOException e) {
            throw new RuntimeException("Could not write log file.", e);
        }
    }

    private static void log(PrintWriter writer, String message) {
        System.out.println(message);
        writer.println(message);
        writer.flush(); 
    }

    private record ExperimentResult(
            int instanceId,
            int[] composition,
            EvaluationHeuristic.WeightConfig bestCfg,
            EvaluationSummary oosSummary
    ) {}

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
     * Builds the list of InstanceConfigs used for sensitivity analyses.
     * The FD instance uses the fleet sizes from the stochastic fleet sizing step.
     * The dispersed instance distributes the same total FSC truck budget
     * proportionally across its FSCs based on their storage capacity.
     */
    private static List<InstanceConfig> buildSensitivityConfigs(Instance fdInstance, Instance dispersedInstance) {
        Map<String, Integer> fdK = new HashMap<>();
        fdK.put("FSC_1", 48);
        fdK.put("FSC_2", 35);

        Map<String, Integer> dispersedK = buildProportionalK(dispersedInstance, 83);

        return List.of(
                new InstanceConfig(fdInstance, 79, fdK, "FD"),
                new InstanceConfig(dispersedInstance, 79, dispersedK, "Dispersed"));
    }

    /**
     * Distributes totalFscTrucks among the FSCs of the given instance proportional
     * to each FSC's maximum storage capacity.
     */
    private static Map<String, Integer> buildProportionalK(Instance instance, int totalFscTrucks) {
        int totalCap = instance.FSCs.stream().mapToInt(fsc -> fsc.maxStorageCapCcls).sum();
        Map<String, Integer> K = new HashMap<>();
        int assigned = 0;
        for (int i = 0; i < instance.FSCs.size() - 1; i++) {
            FSC fsc = instance.FSCs.get(i);
            int trucks = (int) Math.round((double) fsc.maxStorageCapCcls / totalCap * totalFscTrucks);
            K.put(fsc.FSCname, trucks);
            assigned += trucks;
        }
        FSC last = instance.FSCs.get(instance.FSCs.size() - 1);
        K.put(last.FSCname, totalFscTrucks - assigned);
        return K;
    }

    private static void runCorrelationAnalysis(List<InstanceConfig> configs) {
        CorrelationAnalysis.run(configs);
    }

    private static void runDemandDistributionAnalysis(List<InstanceConfig> configs) {
        DemandDistributionAnalysis.run(configs);
    }

    private static void runExtendedTimeHorizon() {
        ExtendedTimeHorizonAnalysis.run();
    }

    private static void runVehicleBreakdownAnalysis() {
        VehicleBreakdownAnalysis.run();
    }
}