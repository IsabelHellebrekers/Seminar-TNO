package Analysis;

import Stochastic.EvaluationHeuristic;
import Stochastic.EvaluationHeuristic.EvaluationSummary;

import java.util.List;

/**
 * Sensitivity analysis for demand correlation: sweeps day-to-day and
 * product-pair correlations and reports the impact on the no-stockout
 * service level.
 *
 * @author 621349it Ies Timmerarends
 * @author 612348ih Isabel Hellebrekers
 * @author 631426ls Lena Stiebing
 * @author 661267eb Eeke Bavelaar
 */
public final class CorrelationAnalysis {

    private CorrelationAnalysis() {}

    private static final int N_SCENARIOS = 10000;
    private static final int OOS_SEED = 10042;

    private static final EvaluationHeuristic.WeightConfig BEST_CFG = new EvaluationHeuristic.WeightConfig(
            new EvaluationHeuristic.TargetWeights(1.2, 0.6, 0.9),
            new EvaluationHeuristic.TargetWeights(1.0, 1.0, 1.0));

    // Correlation values swept in each experiment: from independent to strong
    private static final double[] RHO_VALUES_DAYS = {0.0, 0.05, 0.10, 0.15};
    private static final double[] RHO_VALUES_PRODUCTS = {0.0, 0.25, 0.5, 0.75, 1.0};

    /**
     * Run the full correlation sensitivity analysis for all provided instance configurations.
     *
     * @param configs list of instance configurations to evaluate
     */
    public static void run(List<InstanceConfig> configs) {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("SENSITIVITY ANALYSIS : CORRELATION");
        System.out.println("============================================================");
        System.out.printf("scenarios = %d | seed = %d%n", N_SCENARIOS, OOS_SEED);

        runDayCorrelation(configs);
        runProductCorrelation(configs, "FW-FUEL",   1);
        runProductCorrelation(configs, "FW-AMMO",   2);
        runProductCorrelation(configs, "FUEL-AMMO", 3);
    }

    /**
     * Sweeps day-to-day (AR(1)) correlation while keeping all product correlations at zero.
     * Correlations vector: [rhoDays, 0, 0, 0]
     */
    private static void runDayCorrelation(List<InstanceConfig> configs) {
        System.out.println();
        System.out.println("--- Day-to-day correlation (products independent) ---");
        printHeader("rhoDays");

        for (double rho : RHO_VALUES_DAYS) {
            List<Double> correlations = List.of(rho, 0.0, 0.0, 0.0);
            printRows(configs, "rhoDays", rho, correlations);
        }
    }

    /**
     * Sweeps one pairwise product correlation while keeping day correlation and all other
     * product correlations at zero.
     *
     * @param pairName human-readable label for the product pair (e.g. "FW-FUEL")
     * @param index    position in [rhoDays, rhoFWFUEL, rhoFWAMMO, rhoFUELAMMO] to vary (1, 2, or 3)
     */
    private static void runProductCorrelation(List<InstanceConfig> configs, String pairName, int index) {
        System.out.println();
        System.out.printf("--- Product correlation: %s (days independent, other pairs at 0) ---%n", pairName);
        printHeader("rho_" + pairName);

        for (double rho : RHO_VALUES_PRODUCTS) {
            double[] c = {0.0, 0.0, 0.0, 0.0};
            c[index] = rho;
            List<Double> correlations = List.of(c[0], c[1], c[2], c[3]);
            printRows(configs, "rho_" + pairName, rho, correlations);
        }
    }

    private static void printHeader(String rhoLabel) {
        System.out.printf("%-18s %-20s %-10s %-18s %-22s%n",
                rhoLabel, "Instance", "MSC", "No-stockout(%)", "Avg stockout (kg)");
    }

    private static void printRows(List<InstanceConfig> configs, String rhoLabel, double rhoValue,
                                  List<Double> correlations) {
        for (InstanceConfig cfg : configs) {
            EvaluationSummary summary = EvaluationHeuristic.evaluate(
                    cfg.instance(), cfg.mscTrucks(), cfg.trucksPerFSC(),
                    N_SCENARIOS, OOS_SEED, BEST_CFG, correlations);
            System.out.printf("%-18.2f %-20s %-10d %-18.2f %-22.2f%n",
                    rhoValue, cfg.label(), cfg.mscTrucks(),
                    summary.getNoStockoutPercentage(), summary.getAvgTotalStockoutKg());
        }
    }
}
