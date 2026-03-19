import DataUtils.InstanceCreator;
import DataUtils.OutputCreator;
import Deterministic.CapacitatedResupplyMILP;
import Stochastic.*;
import Stochastic.EvaluationHeuristic.*;
import Objects.*;
import Analysis.*;
import java.util.*;
import java.io.*;

import com.gurobi.gurobi.GRBException;

/**
 * Entry point for the Capacitated Resupply Problem experiments.
 * Runs deterministic MILP, stochastic fleet sizing, perfect hindsight
 * benchmarks, and sensitivity analyses.
 *
 * @author 621349it Ies Timmerarends
 * @author 612348ih Isabel Hellebrekers
 * @author 631426ls Lena Stiebing
 * @author 661267eb Eeke Bavelaar
 */
public class Main {

    private static final int OOS_SEED = 10042;
    private static final int N_OOS = 10000;
    private static final int DISPERSED_INSTANCE_INDEX = 41;

    /**
     * Main entry point. Runs all experiments sequentially.
     *
     * @param args command-line arguments (unused)
     * @throws IOException  if a log file cannot be written
     * @throws GRBException if a Gurobi model error occurs
     */
    public static void main(String[] args) throws IOException, GRBException {
        Instance fdInstance = InstanceCreator.createFDInstance(10).get(0);
        Instance dispersedInstance = InstanceCreator.contiguousPartitions().get(DISPERSED_INSTANCE_INDEX);
        List<Instance> allInstances = List.of(fdInstance, dispersedInstance);

        // MILP
        List<Result> result = CapacitatedResupplyMILP.solveInstances(allInstances);
        for (Result res : result) {
            System.out.println(res.getTruckVector());
        }

        // DISPERSED EXPERIMENTS
        runDispersedExperiments();

        // STOCHASTIC EXPERIMENTS
        runStochasticExperiments(fdInstance);

        // PERFECT HINDSIGHT EXPERIMENTS
        runPerfectHindsightExperiments();

        // SENSITIVITY ANALYSIS
        List<InstanceConfig> sensitivityConfigs = buildExtendedHorizonConfigs();
        runCorrelationAnalysis(sensitivityConfigs);
        runDemandDistributionAnalysis(sensitivityConfigs);

        List<InstanceConfig> timeHorizonConfigs = buildExtendedHorizonConfigs();
        runExtendedTimeHorizon(timeHorizonConfigs);

        List<InstanceConfig> vehicleBreakdownConfigs = buildExtendedHorizonConfigs();
        runVehicleBreakdownAnalysis(vehicleBreakdownConfigs);

        // Stochastic fleet sizing on the base FD instance
        runFleetSizing(fdInstance);

        Instance dispInstance = InstanceCreator.createDispersedInstanceExtraType(2000, 7000, 1000, 10).get(0);
        runStochasticDispersed(dispInstance);
    }

    private static void runDispersedExperiments() {
        long t0 = System.currentTimeMillis();
        List<Result> results = CapacitatedResupplyMILP.solveInstances(InstanceCreator.contiguousPartitions());
        long t1 = System.currentTimeMillis();
        long timeSeconds = (t1 - t0) / 1000;
        System.out.println("Runtime (s) : " + timeSeconds);
        new OutputCreator().createCSV(results);
    }

    private static void runFleetSizing(Instance base) {
        final int nScenarios = 1000;
        final double serviceLevel = 0.95;
        final long seedSizingTrain = 42000;

        System.out.println();
        System.out.println("STOCHASTIC FLEET SIZING, time horizon = " + base.getTimeHorizon());
        StochasticFleetSizerCorrected.FleetSizingResult fleetResult;
        try {
            long t0 = System.currentTimeMillis();
            fleetResult = StochasticFleetSizerCorrected.estimateFleetSizes(base, nScenarios, serviceLevel, seedSizingTrain);
            long t1 = System.currentTimeMillis();
            long timeSeconds = (t1 - t0) / 1000;

            System.out.println("Runtime (s) : " + timeSeconds);
            System.out.println("Total trucks = " + fleetResult.getTotalTrucks());
            int mscTrucks = fleetResult.getTrucksMSCtoFSC() + fleetResult.getTrucksMSCtoVUST();
            System.out.println("MSC =" + mscTrucks);
            for (String w : new TreeSet<>(fleetResult.getTrucksAtFSC().keySet())) {
                System.out.println("  " + w + " = " + fleetResult.getTrucksAtFSC().get(w));
            }
        } catch (GRBException e) {
            throw new RuntimeException();
        }
    }

    private static void runStochasticExperiments(Instance base) {
        final int nScenarios = 1000;
        final double serviceLevel = 0.95;

        final long seedSizingTrain = 42000;
        final long seedTuningTrain = 43000;

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
                log(writer, "Total trucks = " + fleet.getTotalTrucks());

                int totalMscTrucks = fleet.getTrucksMSCtoFSC() + fleet.getTrucksMSCtoVUST();
                log(writer, "MSC = " + totalMscTrucks);

                for (String w : new TreeSet<>(fleet.getTrucksAtFSC().keySet())) {
                    log(writer, "  " + w + " = " + fleet.getTrucksAtFSC().get(w));
                }
            } catch (GRBException e) {
                log(writer, "ERROR in STEP 1: " + e.getMessage());
                throw new RuntimeException(e);
            }

            final int mscTrucks = fleet.getTrucksMSCtoFSC() + fleet.getTrucksMSCtoVUST();
            final Map<String, Integer> trucksPerFsc = new HashMap<>(fleet.getTrucksAtFSC());

            log(writer, "");
            log(writer, "STEP 2 : WEIGHT TUNING (train)");

            double lb = 0.5, ub = 1.5, step = 0.1;
            EvaluationHeuristic.TargetWeights defaultVust = new EvaluationHeuristic.TargetWeights(1.0, 1.0, 1.0);

            var tuning = EvaluationHeuristic.tuneWeights(
                    base, mscTrucks, trucksPerFsc, nScenarios, (int) seedTuningTrain,
                    lb, ub, step, defaultVust);

            log(writer, "BEST CONFIG : OU = " + tuning.getBestCfg().ou() +
                    " | VUST = " + tuning.getBestCfg().vust());

            EvaluationHeuristic.WeightConfig bestCfg = tuning.getBestCfg();

            log(writer, "");
            log(writer, "STEP 3 : OOS EVALUATION 3 CCLs (test)");

            EvaluationSummary oosSummary = EvaluationHeuristic.evaluate(
                    base, mscTrucks, trucksPerFsc, N_OOS, OOS_SEED, bestCfg,
                    List.of(0.0, 0.0, 0.0, 0.0));

            log(writer, oosSummary.toString());

            log(writer, "");
            log(writer, "STEP 4 + 5 : LOOP OVER 10 INSTANCES WITH DIFFERENT 4TH CCL COMPOSITIONS");

            List<int[]> compositions = List.of(
                    new int[] { 3000, 7000, 0 },
                    new int[] { 2000, 7000, 1000 },
                    new int[] { 1000, 7000, 2000 },
                    new int[] { 0, 7000, 3000 },
                    new int[] { 2000, 8000, 0 },
                    new int[] { 1000, 8000, 1000 },
                    new int[] { 0, 8000, 2000 },
                    new int[] { 1000, 9000, 0 },
                    new int[] { 0, 9000, 1000 },
                    new int[] { 0, 10000, 0 });

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

                // Reuse the same train seed for all CCL variants so results are comparable
                var tuning4 = EvaluationHeuristic.tuneWeights(
                        inst, mscTrucks, trucksPerFsc, nScenarios, (int) seedTuningTrain,
                        lb, ub, step, defaultVust);

                log(writer, "BEST CONFIG : OU = " + tuning4.getBestCfg().ou() +
                        " | VUST = " + tuning4.getBestCfg().vust());

                EvaluationHeuristic.WeightConfig bestCfg4 = tuning4.getBestCfg();

                EvaluationSummary oos4 = EvaluationHeuristic.evaluate(
                        inst, mscTrucks, trucksPerFsc, N_OOS, OOS_SEED, bestCfg4,
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

    private static void runStochasticDispersed(Instance instance) {
        final int dispersedMscTrucks = 88;
        final int dispersedFsc1Trucks = 33;
        final int dispersedFsc2Trucks = 18;
        final int dispersedFsc3Trucks = 24;
        final int dispersedFsc4Trucks = 17;

        final Map<String, Integer> trucksPerFsc = new HashMap<>();
        trucksPerFsc.put("FSC_1", dispersedFsc1Trucks);
        trucksPerFsc.put("FSC_2", dispersedFsc2Trucks);
        trucksPerFsc.put("FSC_3", dispersedFsc3Trucks);
        trucksPerFsc.put("FSC_4", dispersedFsc4Trucks);

        EvaluationHeuristic.WeightConfig config = new WeightConfig(
            new TargetWeights(1.2, 0.6, 0.9),
            new TargetWeights(1.0, 1.0, 1.0)
        );

        EvaluationSummary oos = EvaluationHeuristic.evaluate(
            instance, dispersedMscTrucks, trucksPerFsc, N_OOS, OOS_SEED, config, List.of(0.0, 0.0, 0.0, 0.0));

        System.out.println(oos);
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
            EvaluationSummary oosSummary) {
    }

    private static void runPerfectHindsightExperiments() throws GRBException {
        final int fdMscTrucks = 79;
        final int fdFsc1Trucks = 48;
        final int fdFsc2Trucks = 35;

        final Map<String, Integer> trucksPerFsc = new HashMap<>();
        trucksPerFsc.put("FSC_1", fdFsc1Trucks);
        trucksPerFsc.put("FSC_2", fdFsc2Trucks);

        Instance base3 = InstanceCreator.createFDInstance().get(0);

        System.out.println();
        System.out.println("--- Perfect hindsight: 3 CCL types ---");
        System.out.println("N=" + N_OOS + " | seed=" + OOS_SEED + " | fixed fleet: M=" + fdMscTrucks + " K=" + trucksPerFsc);

        PerfectHindsight.run(base3, N_OOS, OOS_SEED, fdMscTrucks, trucksPerFsc);

        Instance base4 = InstanceCreator.createFDInstanceExtraType(2000, 7000, 1000).get(0);

        System.out.println();
        System.out.println("--- Perfect hindsight: 4 CCL types (CCL4=2000/7000/1000) ---");
        System.out.println("N=" + N_OOS + " | seed=" + OOS_SEED + " | fixed fleet: M=" + fdMscTrucks + " K=" + trucksPerFsc);

        PerfectHindsight.run(base4, N_OOS, OOS_SEED, fdMscTrucks, trucksPerFsc);
    }

    private static List<InstanceConfig> buildExtendedHorizonConfigs() {
        final int fdMscTrucks = 79;
        final int fdFsc1Trucks = 48;
        final int fdFsc2Trucks = 35;

        Map<String, Integer> fdTrucksPerFsc = new HashMap<>();
        fdTrucksPerFsc.put("FSC_1", fdFsc1Trucks);
        fdTrucksPerFsc.put("FSC_2", fdFsc2Trucks);

        CCLPackage ccl4 = new CCLPackage(4, 2000, 7000, 1000);
        Instance fdInstance4 = InstanceCreator.createFDInstanceExtraType(2000, 7000, 1000, 10).get(0);

        return List.of(new InstanceConfig(fdInstance4, fdMscTrucks, fdTrucksPerFsc, "FD (4 CCL)", ccl4));
    }

    private static void runCorrelationAnalysis(List<InstanceConfig> configs) {
        CorrelationAnalysis.run(configs);
    }

    private static void runDemandDistributionAnalysis(List<InstanceConfig> configs) {
        DemandDistributionAnalysis.run(configs);
    }

    private static void runExtendedTimeHorizon(List<InstanceConfig> configs) {
        ExtendedTimeHorizonAnalysis.run(configs);
    }

    private static void runVehicleBreakdownAnalysis(List<InstanceConfig> configs) {
        VehicleBreakdownAnalysis.run(configs);
    }
}