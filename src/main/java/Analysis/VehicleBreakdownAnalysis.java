package Analysis;

import DataUtils.InstanceCreator;
import Objects.Instance;
import Stochastic.EvaluationHeuristic;
import Stochastic.EvaluationHeuristic.EvaluationSummary;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VehicleBreakdownAnalysis {

    private VehicleBreakdownAnalysis() {
    }

    // Baseline fleet
    private static final int BASE_MSC_TRUCKS = 79;
    private static final int BASE_FSC1_TRUCKS = 48;
    private static final int BASE_FSC2_TRUCKS = 35;

    // Fixed heuristic settings
    private static final EvaluationHeuristic.TargetWeights OU_WEIGHTS = new EvaluationHeuristic.TargetWeights(1.3, 0.7,
            1.3);

    private static final EvaluationHeuristic.TargetWeights VUST_WEIGHTS = new EvaluationHeuristic.TargetWeights(1.0,
            1.0, 1.2);

    // Fixed 4th CCL type
    private static final int CCL4_FW = 2000;
    private static final int CCL4_FUEL = 6000;
    private static final int CCL4_AMMO = 2000;

    // Evaluation settings
    private static final int HORIZON = 10;
    private static final int N_SCENARIOS = 1000;
    private static final long OOS_SEED = 10042L;

    // No-demand-correlation setting
    private static final List<Double> ZERO_CORRELATIONS = List.of(0.0, 0.0, 0.0, 0.0);

    // Breakdown rates for fixed-reduction model
    private static final double[] BREAKDOWN_RATES = { 0.00, 0.01, 0.05, 0.10, 0.15 };

    /**
     * Public entry point for the vehicle breakdown extension.
     */
    public static void run() {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("VEHICLE BREAKDOWN ANALYSIS");
        System.out.println("============================================================");

        runFixedReductionBreakdowns3CCL();
        runFixedReductionBreakdowns4CCL();
    }

    /**
     * Fixed fleet reduction analysis for the 3-CCL configuration.
     */
    private static void runFixedReductionBreakdowns3CCL() {
        Instance inst = InstanceCreator
                .createFDInstance(HORIZON)
                .get(0);

        runFixedReductionBreakdowns(inst, "3 CCL TYPES");
    }

    /**
     * Fixed fleet reduction analysis for the 4-CCL configuration.
     */
    private static void runFixedReductionBreakdowns4CCL() {
        Instance inst = InstanceCreator
                .createFDInstanceExtraType(CCL4_FW, CCL4_FUEL, CCL4_AMMO, HORIZON)
                .get(0);

        runFixedReductionBreakdowns(inst, "4 CCL TYPES (CCL4 = 2000, 6000, 2000)");
    }

    /**
     * Variant A: model vehicle breakdowns as a fixed proportional reduction
     * in the available fleet.
     */
    private static void runFixedReductionBreakdowns(Instance inst, String configLabel) {
        System.out.println();
        System.out.println("------------------------------------------------------------");
        System.out.println("STOCHASTIC VEHICLE BREAKDOWNS - FIXED FLEET REDUCTION");
        System.out.println("CONFIGURATION: " + configLabel);
        System.out.println("------------------------------------------------------------");

        EvaluationHeuristic.WeightConfig cfg = new EvaluationHeuristic.WeightConfig(OU_WEIGHTS, VUST_WEIGHTS);

        System.out.println("Baseline settings:");
        System.out.println("Horizon       = " + HORIZON);
        System.out.println("Scenarios     = " + N_SCENARIOS);
        System.out.println("OOS seed      = " + OOS_SEED);
        System.out.println("Baseline MSC  = " + BASE_MSC_TRUCKS);
        System.out.println("Baseline FSC1 = " + BASE_FSC1_TRUCKS);
        System.out.println("Baseline FSC2 = " + BASE_FSC2_TRUCKS);
        System.out.println("OU weights    = " + OU_WEIGHTS);
        System.out.println("VUST weights  = " + VUST_WEIGHTS);

        if (inst.cclTypes.size() == 4) {
            System.out.println("CCL4          = (" + CCL4_FW + ", " + CCL4_FUEL + ", " + CCL4_AMMO + ")");
        }

        System.out.println();
        System.out.printf("%-12s %-10s %-10s %-10s %-18s %-22s %-22s%n",
                "Breakdown", "MSC", "FSC_1", "FSC_2", "No-stockout(%)",
                "Scenarios w/o stockout", "Avg total stockout kg");

        for (double p : BREAKDOWN_RATES) {
            int mEff = reducedFleet(BASE_MSC_TRUCKS, p);
            int k1Eff = reducedFleet(BASE_FSC1_TRUCKS, p);
            int k2Eff = reducedFleet(BASE_FSC2_TRUCKS, p);

            Map<String, Integer> kEff = new HashMap<>();
            kEff.put("FSC_1", k1Eff);
            kEff.put("FSC_2", k2Eff);

            EvaluationSummary summary = EvaluationHeuristic.evaluate(
                    inst, mEff, kEff, N_SCENARIOS, OOS_SEED, cfg, ZERO_CORRELATIONS);

            System.out.printf("%-12s %-10d %-10d %-10d %-18.2f %-22d %-22.2f%n",
                    formatPercent(p),
                    mEff,
                    k1Eff,
                    k2Eff,
                    summary.noStockoutPercentage,
                    summary.scenariosWithoutStockout,
                    summary.avgTotalStockoutKg);
        }
    }

    /**
     * Deterministic fixed-reduction approximation of breakdowns.
     * We round to the nearest integer and keep the value nonnegative.
     */
    private static int reducedFleet(int baseFleet, double breakdownRate) {
        return Math.max(0, (int) Math.round(baseFleet * (1.0 - breakdownRate)));
    }

    private static String formatPercent(double p) {
        return String.format("%.0f%%", 100.0 * p);
    }
}