import DataUtils.InstanceCreator;
import DataUtils.OutputCreator;
import Deterministic.CapacitatedResupplyMILP;
import Stochastic.*;
import Stochastic.EvaluationHeuristic.EvaluationSummary;
import Objects.*;
import java.util.*;

import java.io.IOException;

public class Main {

    private static void printProgressBar(int current, int total, long startTimeMs) {
        int barWidth = 40;

        double progress = (double) current / total;
        int filled = (int) (barWidth * progress);

        long elapsed = System.currentTimeMillis() - startTimeMs;
        double avgTimePerStep = (current == 0) ? 0.0 : (double) elapsed / current;
        long eta = (long) (avgTimePerStep * (total - current));

        String bar = "[" + "=".repeat(filled) + " ".repeat(barWidth - filled) + "]";

        System.out.printf(
                "\r%s %3d%% | %d/%d | Elapsed: %.1fs | ETA: %.1fs",
                bar,
                (int) (progress * 100),
                current,
                total,
                elapsed / 1000.0,
                eta / 1000.0);

        if (current == total)
            System.out.println();
    }

    public static void main(String[] args) throws IOException {
        InstanceCreator ic = new InstanceCreator();
        Instance data = ic.createFDInstance().get(0);

        // Solve FD Concept (Deterministic MILP)
        // List<Result> results = CapacitatedResupplyMILP.solveInstances(new
        // InstanceCreator().createFDInstance());

        // Solve Dispersed Concept (Deterministic MILP for contiguous partitions)
        // List<Result> results = CapacitatedResupplyMILP.solveInstances(new
        // InstanceCreator().contiguousPartitions());
        // new OutputCreator().createCSV(results);

        // Part 1 results : fleet size
        int M = 78; // 78
        Map<String, Integer> K = new HashMap<>();
        K.put("FSC_1", 48); // 48
        K.put("FSC_2", 34); // 34

        int nWeights = 1000;
        int weightsSeed = 42;

        // double lb = 0.5, ub = 1.5, step = 0.1;

        // EvaluationHeuristic.TargetWeights defaultVust = new
        // EvaluationHeuristic.TargetWeights(1.0, 1.0, 1.0);

        // var res = EvaluationHeuristic.tuneWeights(
        // data, M, K, nWeights, weightsSeed,
        // lb, ub, step, defaultVust);

        // System.out.println("BEST CONFIG : OU=" + res.bestCfg.ou() + " VUST=" +
        // res.bestCfg.vust());

        EvaluationHeuristic.WeightConfig bestCfg = new EvaluationHeuristic.WeightConfig(
                new EvaluationHeuristic.TargetWeights(1.3, 0.6, 1.3),
                new EvaluationHeuristic.TargetWeights(1.0, 0.9, 1.1));

        int nOOS = 1000;
        int oosSeed = 10042;

        // EvaluationSummary oosSummary = EvaluationHeuristic.evaluate(
        // data, M, K,
        // nOOS, oosSeed,
        // bestCfg);

        // System.out.println("OOS summary :");
        // System.out.println(oosSummary);

        // EvaluationReporter.reportStockouts(data, M, K, nOOS, oosSeed, bestCfg);

        // int nComposition = 1000;
        // int compositionSeed = 20042;

        // int stepKg = 1000;

        // var resComposition = EvaluationHeuristic.gridSearchCCL(
        // M, K, nComposition, compositionSeed, bestCfg, stepKg
        // );

        // System.out.println("Chosen CCL 4 composition : " + resComposition.bestComp);

        Instance best = InstanceCreator.createFDInstanceExtraType(
                1000,
                6000,
                3000).get(0);

        var oos = EvaluationHeuristic.evaluate(best, M, K, nOOS, oosSeed, bestCfg);
        System.out.println("OOS with best CCL4 : " + oos);

        // EvaluationReporter.reportStockouts(best, M, K, nOOS, oosSeed, bestCfg);

    }
}