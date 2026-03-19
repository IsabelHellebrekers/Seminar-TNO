package Analysis;

import Stochastic.EvaluationHeuristic;
import Stochastic.EvaluationHeuristic.EvaluationSummary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sensitivity analysis for vehicle breakdowns: evaluates service level when
 * truck fleet sizes are reduced by fixed percentage rates.
 *
 * @author 621349it Ies Timmerarends
 * @author 612348ih Isabel Hellebrekers
 * @author 631426ls Lena Stiebing
 * @author 661267eb Eeke Bavelaar
 */
public final class VehicleBreakdownAnalysis {

    private VehicleBreakdownAnalysis() {
    }

    private static final EvaluationHeuristic.TargetWeights OU_WEIGHTS =
            new EvaluationHeuristic.TargetWeights(1.2, 0.6, 0.9);

    private static final EvaluationHeuristic.TargetWeights VUST_WEIGHTS =
            new EvaluationHeuristic.TargetWeights(1.0, 1.0, 1.0);

    private static final int N_SCENARIOS = 10000;
    private static final int OOS_SEED = 10042;

    private static final List<Double> ZERO_CORRELATIONS = List.of(0.0, 0.0, 0.0, 0.0);

    private static final double[] BREAKDOWN_RATES = {0.00, 0.01, 0.05, 0.10, 0.15, 0.20, 0.25};

    /**
     * Run the vehicle breakdown analysis for all provided instance configurations.
     *
     * @param configs list of instance configurations to evaluate
     */
    public static void run(List<InstanceConfig> configs) {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("VEHICLE BREAKDOWN ANALYSIS");
        System.out.println("============================================================");

        for (InstanceConfig cfg : configs) {
            runFixedReductionBreakdowns(cfg);
        }
    }

    /**
     * Fixed-reduction breakdown model: reduce all truck counts by a fixed percentage.
     */
    private static void runFixedReductionBreakdowns(InstanceConfig cfg) {
        System.out.println();
        System.out.println("------------------------------------------------------------");
        System.out.println("STOCHASTIC VEHICLE BREAKDOWNS - FIXED FLEET REDUCTION");
        System.out.println("INSTANCE: " + cfg.label());
        System.out.println("------------------------------------------------------------");

        EvaluationHeuristic.WeightConfig weightCfg = new EvaluationHeuristic.WeightConfig(OU_WEIGHTS, VUST_WEIGHTS);

        List<String> fscNames = new ArrayList<>(cfg.trucksPerFSC().keySet());
        Collections.sort(fscNames);

        System.out.println("Baseline settings:");
        System.out.println("Scenarios     = " + N_SCENARIOS);
        System.out.println("OOS seed      = " + OOS_SEED);
        System.out.println("Baseline MSC  = " + cfg.mscTrucks());
        for (String fsc : fscNames) {
            System.out.println("Baseline " + fsc + " = " + cfg.trucksPerFSC().get(fsc));
        }
        System.out.println("OU weights    = " + OU_WEIGHTS);
        System.out.println("VUST weights  = " + VUST_WEIGHTS);

        // Build header: fixed columns + one column per FSC
        StringBuilder headerFmt = new StringBuilder("%-12s %-10s");
        List<Object> headerArgs = new ArrayList<>(List.of("Breakdown", "MSC"));
        for (String fsc : fscNames) {
            headerFmt.append(" %-10s");
            headerArgs.add(fsc);
        }
        headerFmt.append(" %-18s %-22s %-22s%n");
        headerArgs.addAll(List.of("No-stockout(%)", "Scenarios w/o stockout", "Avg total stockout kg"));
        System.out.println();
        System.out.printf(headerFmt.toString(), headerArgs.toArray());

        for (double p : BREAKDOWN_RATES) {
            int mEff = reducedFleet(cfg.mscTrucks(), p);

            Map<String, Integer> kEff = new HashMap<>();
            for (String fsc : fscNames) {
                kEff.put(fsc, reducedFleet(cfg.trucksPerFSC().get(fsc), p));
            }

            EvaluationSummary summary = EvaluationHeuristic.evaluate(
                    cfg.instance(), mEff, kEff, N_SCENARIOS, OOS_SEED, weightCfg, ZERO_CORRELATIONS);

            StringBuilder rowFmt = new StringBuilder("%-12s %-10d");
            List<Object> rowArgs = new ArrayList<>(List.of(formatPercent(p), mEff));
            for (String fsc : fscNames) {
                rowFmt.append(" %-10d");
                rowArgs.add(kEff.get(fsc));
            }
            rowFmt.append(" %-18.2f %-22d %-22.2f%n");
            rowArgs.addAll(List.of(
                    summary.getNoStockoutPercentage(),
                    summary.getScenariosWithoutStockout(),
                    summary.getAvgTotalStockoutKg()));
            System.out.printf(rowFmt.toString(), rowArgs.toArray());
        }
    }

    private static int reducedFleet(int baseFleet, double breakdownRate) {
        return Math.max(0, (int) Math.round(baseFleet * (1.0 - breakdownRate)));
    }

    private static String formatPercent(double p) {
        return String.format("%.0f%%", 100.0 * p);
    }
}