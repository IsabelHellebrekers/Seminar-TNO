package Stochastic.general_approach;

import Objects.Instance;

import java.util.List;

public class OutOfSampleSolver {

    private final int nSamples;
    private final long baseSeed;

    public OutOfSampleSolver(int nSamples, long baseSeed) {
        this.nSamples = nSamples;
        this.baseSeed = baseSeed;
    }

    public void run(Instance baseInstance) {
        int noStockoutCount = 0;

        for (int s = 1; s <= this.nSamples; s++) {
            long seed = this.baseSeed + s;

            Instance scenarioInstance = baseInstance;

            RollingHorizonStockoutEvaluator evaluator = new RollingHorizonStockoutEvaluator(seed, new OnePeriodMILP());

            List<StockOut> stockouts = evaluator.evaluate(scenarioInstance);
            if (stockouts.isEmpty()) {
                noStockoutCount++;
            }

            double progressPercentage = 100.0 * s / this.nSamples;
            double noStockoutPercentage = 100.0 * noStockoutCount / s;

            System.out.printf(
                    "Out-of-sample progress: %d/%d (%.1f%%). No-stockout so far: %.1f%%%n",
                    s, this.nSamples, progressPercentage, noStockoutPercentage
            );
        }

        double finalNoStockoutPct = 100.0 * noStockoutCount / nSamples;
        System.out.printf(
                "Completed %d simulations. Final no-stockout percentage (10-period horizon): %.1f%%%n",
                nSamples, finalNoStockoutPct
        );
    }
}
