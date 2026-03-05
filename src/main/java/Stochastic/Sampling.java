package Stochastic;
import java.io.IOException;
import java.util.Random;

/**
 * Random demand sampler used for scenario generation in the stochastic evaluation.
 * 
 * Distributions (multipliers applied to deterministic daily demand in kg):
 *  - Food & Water (FW): Uniform[minUni, maxUni]
 *  - Fuel (FUEL): Binomial(n, p) approximated via Bernoulli trials (scaled)
 *  - Ammunition (AMMO): Triangular(minTri, modeTri, maxTri) via inverse CDF
 */
public final class Sampling {

    // Random number generator (seeded for reproducibility in experiments)
    private final Random rng;

    // Uniform multiplier bounds for FW demand
    private final double minUni = 0.8;
    private final double maxUni = 1.2;

    // Binomial parameters for fuel multiplier (implemented via n Bernoulli trials)
    private final double n = 25;
    private final double p = 0.4;

    // Triangular multiplier parameters for AMMO demand
    private final double minTri = 0.2;
    private final double maxTri = 2.0;
    private final double modeTri = 0.8;

    /**
     * Construct a sampler with a non-deterministic seed.
     */
    public Sampling() {
        this.rng = new Random(); 
    }

    /**
     * Construct a sampler with a fixed seed to ensure reproducibility across runs.
     * @param seed the fixed seed
     */
    public Sampling(long seed) {
        this.rng = new Random(seed);
    }

    /**
     * Draw a Uniform(minUni, maxUni) multiplier for FW demand.
     */
    public double uniform() {
        return minUni + rng.nextDouble() * (maxUni - minUni);
    }

    /**
     * Upper (1 - epsilon) quantile of Uniform(minUni, maxUni).
     * @param epsilon the epsilon value
     * @return the upper (1 - epsilon) quantile
     */
    public double uniformQuantile(double epsilon) {
        double alpha = 1.0 - epsilon;
        return minUni + alpha * (maxUni - minUni);
    }

    /**
     * Generate a 10-day FW demand path in kg, by multiplying the deterministic daily demand
     * with i.i.d. Uniform multipliers.
     * @param dailyFoodWaterKg deterministic daily demand for FW
     * @return 10-day FW demand path in kg
     */
    public double[] stochasticFW(int dailyFoodWaterKg) {
        double[] FW = new double[10];
        for (int i = 0; i < FW.length; i++) {
            FW[i] = (dailyFoodWaterKg * uniform());
        }

        return FW;
    }

    /**
     * Draw a Binomial(n, p) based multiplier (implemetned via n Bernoulli trials),
     * then scaled to math the fuel modeling convention used in this project.
     */
    public double binomial() {
        int successes = 0;
        for (int i = 0; i < n; i++) {
            if (rng.nextDouble() < p) successes++;
        }
        return successes*1.0/10;
    }

    /**
     * Upper (1 - epsilon) quantile for the implemented Binomial(n, p) mechanism (scaled).
     * Uses iterative CDF accumulation.
     * @param epsilon the epsilon value
     * @return the upper (1 - epsilon) quantile
     */
    public double binomialQuantile(double epsilon) {
        double alpha = 1.0 - epsilon;
        double prob = Math.pow(1-p, n);
        double cdf = prob;
        double i = 0.0;
        while (cdf < alpha) {
            prob = prob * (n - i) / (i + 1.0) * p / (1.0 - p);
            cdf += prob;
            i += 1.0;
        }
        return i / 10;
    }

    /**
     * Generate a 10-day FUEL demand path in kg, by multiplying the deterministic daily demand
     * with i.i.d. Binomial-based multipliers.
     * @param dailyFuelKg deterministic daily demand for FUEL
     * @return 10-day FUEL demand path in kg
     */
    public double[] stochasticFUEL(int dailyFuelKg) {
        double[] FUEL = new double[10];
        for (int i = 0; i < FUEL.length; i++) {
            FUEL[i] = (dailyFuelKg * binomial());
        }

        return FUEL;
    }

    /**
     * Draw a Triangular(minTri, modeTri, maxTri) multiplier via inverse CDF sampling.
     * Requires minTri <= modeTri <= maxTri
     */
    public double triangular() {
        double u = rng.nextDouble();
        double c = (modeTri - minTri) / (maxTri - minTri);

        if (u < c) {
            return minTri + Math.sqrt(u * (maxTri - minTri) * (modeTri - minTri));
        } else {
            return maxTri - Math.sqrt((1.0 - u) * (maxTri - minTri) * (maxTri - modeTri));
        }
    }

    /**
     * Upper (1 - epsilon) quantile of Triangular(minTri, modeTri, maxTri).
     * @param epsilon the epsilon value
     * @return the upper (1 - epsilon) quantile
     */
    public double triangularQuantile(double epsilon) {
        double alpha = 1.0 - epsilon;
        if (alpha <= (modeTri - minTri) / (maxTri - minTri)) {
            return minTri + Math.sqrt((maxTri - minTri) * (modeTri - minTri));
        }
        else {
            return maxTri - Math.sqrt(epsilon * (maxTri - minTri) * (maxTri - modeTri));
        }
    }

    /**
     * Generate a 10-day AMMO demand path in kg, by multiplying the deterministic daily demand
     * with i.i.d. Triangular multipliers.
     * @param dailyAmmoKg deterministic daily demand for AMMO
     * @return 10-day AMMO demand path in kg
     */
    public double[] stochasticAMMO(int dailyAmmoKg) {
        double[] AMMO = new double[10];
        for (int i = 0; i < AMMO.length; i++) {
            AMMO[i] = (dailyAmmoKg * triangular());
        }

        return AMMO;
    }

}
