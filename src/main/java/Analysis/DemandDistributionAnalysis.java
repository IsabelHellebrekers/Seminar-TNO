package Analysis;

import Stochastic.EvaluationHeuristic;
import Stochastic.EvaluationHeuristic.EvaluationSummary;

import java.util.List;

public final class DemandDistributionAnalysis {

    private DemandDistributionAnalysis() {}

    private static final int N_SCENARIOS = 1000;
    private static final int OOS_SEED = 10042;

    private static final EvaluationHeuristic.WeightConfig BEST_CFG = new EvaluationHeuristic.WeightConfig(
            new EvaluationHeuristic.TargetWeights(1.3, 0.7, 1.3),
            new EvaluationHeuristic.TargetWeights(1.0, 1.0, 1.2));

    private static final List<Double> NO_CORRELATION = List.of(0.0, 0.0, 0.0, 0.0);

    private static final List<Double> DEMAND_MULTIPLIERS = List.of(0.9, 0.95, 1.0, 1.05, 1.1);

    public static void run(List<InstanceConfig> configs) {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("SENSITIVITY ANALYSIS : DEMAND DISTRIBUTIONS (no correlation)");
        System.out.println("============================================================");
        System.out.printf("scenarios = %d | seed = %d%n", N_SCENARIOS, OOS_SEED);

        runMeanSensitivity(configs);
        runStdSensitivity(configs);
    }

    private static void runMeanSensitivity(List<InstanceConfig> configs) {
        System.out.println();
        System.out.println("--- Mean sensitivity (stdMult = 1.0) ---");
        System.out.printf("%-12s %-20s %-10s %-18s %-22s%n",
                "meanMult", "Instance", "MSC", "No-stockout(%)", "Avg stockout (kg)");

        for (double mult : DEMAND_MULTIPLIERS) {
            for (InstanceConfig cfg : configs) {
                EvaluationSummary summary = EvaluationHeuristic.evaluate(
                        cfg.instance(), cfg.mscTrucks(), cfg.trucksPerFSC(), N_SCENARIOS, OOS_SEED,
                        BEST_CFG, NO_CORRELATION, mult, 1.0);
                System.out.printf("%-12.2f %-20s %-10d %-18.2f %-22.2f%n",
                        mult, cfg.label(), cfg.mscTrucks(), summary.noStockoutPercentage, summary.avgTotalStockoutKg);
            }
        }
    }

    private static void runStdSensitivity(List<InstanceConfig> configs) {
        System.out.println();
        System.out.println("--- Std sensitivity (meanMult = 1.0) ---");
        System.out.printf("%-12s %-20s %-10s %-18s %-22s%n",
                "stdMult", "Instance", "MSC", "No-stockout(%)", "Avg stockout (kg)");

        for (double mult : DEMAND_MULTIPLIERS) {
            for (InstanceConfig cfg : configs) {
                EvaluationSummary summary = EvaluationHeuristic.evaluate(
                        cfg.instance(), cfg.mscTrucks(), cfg.trucksPerFSC(), N_SCENARIOS, OOS_SEED,
                        BEST_CFG, NO_CORRELATION, 1.0, mult);
                System.out.printf("%-12.2f %-20s %-10d %-18.2f %-22.2f%n",
                        mult, cfg.label(), cfg.mscTrucks(), summary.noStockoutPercentage, summary.avgTotalStockoutKg);
            }
        }
    }
}
