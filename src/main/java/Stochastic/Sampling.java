package Stochastic;

import java.util.Random;

/**
 * Random demand sampler used for scenario generation in the stochastic
 * evaluation.
 * 
 * Distributions (multipliers applied to deterministic daily demand in kg):
 * - Food & Water (FW): Uniform[minUni, maxUni]
 * - Fuel (FUEL): Binomial(n, p) approximated via Bernoulli trials (scaled)
 * - Ammunition (AMMO): Triangular(minTri, modeTri, maxTri) via inverse CDF
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

    // Sensitivity multipliers: meanMult shifts the mean, stdMult scales the spread
    // around the mean.
    // All base distributions have mean = 1.0, so: result = meanMult + stdMult *
    // (raw - 1.0).
    private double meanMult = 1.0;
    private double stdMult = 1.0;

    /**
     * Construct a sampler with a non-deterministic seed.
     */
    public Sampling() {
        this.rng = new Random();
    }

    /**
     * Construct a sampler with a fixed seed to ensure reproducibility across runs.
     * 
     * @param seed the fixed seed
     */
    public Sampling(long seed) {
        this.rng = new Random(seed);
    }

    /**
     * Set the mean multiplier. A value of m shifts the mean of every distribution
     * from 1.0 to m while keeping the spread unchanged (stdMult = 1.0 by default).
     * 
     * @param m the mean multiplier
     */
    public void setMeanMultiplier(double m) {
        this.meanMult = m;
    }

    /**
     * Set the standard-deviation multiplier. A value of s scales the spread of
     * every
     * distribution around its mean by s while keeping the mean unchanged (meanMult
     * = 1.0
     * by default).
     * 
     * @param s the standard-deviation multiplier
     */
    public void setStdMultiplier(double s) {
        this.stdMult = s;
    }

    /**
     * Apply the mean/std multipliers to a raw multiplier drawn from a base
     * distribution
     * whose mean is 1.0: result = meanMult + stdMult * (raw - 1.0).
     */
    private double applyMultipliers(double raw) {
        return meanMult + stdMult * (raw - 1.0);
    }

    /**
     * Draw a Uniform(minUni, maxUni) multiplier for FW demand.
     */
    public double uniform() {
        double raw = minUni + rng.nextDouble() * (maxUni - minUni);
        return applyMultipliers(raw);
        // return raw;
    }

    /**
     * Generate a 10-day FW demand path in kg, by multiplying the deterministic
     * daily demand
     * with i.i.d. Uniform multipliers.
     * 
     * @param dailyFoodWaterKg deterministic daily demand for FW
     * @return 10-day FW demand path in kg
     */
    public double[] stochasticFW(int dailyFoodWaterKg, int horizon) {
        double[] FW = new double[horizon];
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
            if (rng.nextDouble() < p)
                successes++;
        }
        return applyMultipliers(successes * 1.0 / 10);
        // return successes * 1.0 / 10;

    }

    /**
     * Generate a 10-day FUEL demand path in kg, by multiplying the deterministic
     * daily demand
     * with i.i.d. Binomial-based multipliers.
     * 
     * @param dailyFuelKg deterministic daily demand for FUEL
     * @return 10-day FUEL demand path in kg
     */
    public double[] stochasticFUEL(int dailyFuelKg, int horizon) {
        double[] FUEL = new double[horizon];
        for (int i = 0; i < FUEL.length; i++) {
            FUEL[i] = (dailyFuelKg * binomial());
        }

        return FUEL;
    }

    /**
     * Draw a Triangular(minTri, modeTri, maxTri) multiplier via inverse CDF
     * sampling.
     * Requires minTri <= modeTri <= maxTri
     */
    public double triangular() {
        double u = rng.nextDouble();
        double c = (modeTri - minTri) / (maxTri - minTri);
        double raw;
        if (u < c) {
            raw = minTri + Math.sqrt(u * (maxTri - minTri) * (modeTri - minTri));
        } else {
            raw = maxTri - Math.sqrt((1.0 - u) * (maxTri - minTri) * (maxTri - modeTri));
        }
        return applyMultipliers(raw);
        // return raw;
    }

    /**
     * Generate a 10-day AMMO demand path in kg, by multiplying the deterministic
     * daily demand
     * with i.i.d. Triangular multipliers.
     * 
     * @param dailyAmmoKg deterministic daily demand for AMMO
     * @return 10-day AMMO demand path in kg
     */
    public double[] stochasticAMMO(int dailyAmmoKg, int horizon) {
        double[] AMMO = new double[horizon];
        for (int i = 0; i < AMMO.length; i++) {
            AMMO[i] = (dailyAmmoKg * triangular());
        }

        return AMMO;
    }

    /**
     * Generate a 10-day correlated samples for FW, FUEL, and AMMO.
     * Day-to-day correlation is enforced through an AR(1) process with parameter
     * {@code rhoDays}, while
     * product-wise correlation is introduced via a Gaussian copula with the
     * provided pairwise rhos.
     *
     * @param rhoDays     AR(1) persistence between consecutive days
     * @param rhoFWFUEL   correlation between FW and FUEL
     * @param rhoFWAMMO   correlation between FW and AMMO
     * @param rhoFUELAMMO correlation between FUEL and AMMO
     * @return 3x10 matrix of multipliers where the first index is the product
     */
    public double[][] correlatedSamples(double rhoDays, double rhoFWFUEL, double rhoFWAMMO, double rhoFUELAMMO,
            int horizon) {
        final int products = 3;
        final int days = horizon;
        double[][] samples = new double[products][days];

        double[][] correlation = {
                { 1.0, rhoFWFUEL, rhoFWAMMO },
                { rhoFWFUEL, 1.0, rhoFUELAMMO },
                { rhoFWAMMO, rhoFUELAMMO, 1.0 }
        };

        double[][] lower = choleskyLower(correlation);
        double innovationScale = Math.sqrt(Math.max(0.0, 1.0 - rhoDays * rhoDays));

        double[] previous = new double[products];
        double[] current = new double[products];

        for (int day = 0; day < days; day++) {
            double[] innovation = correlatedGaussian(lower);
            if (day == 0) {
                System.arraycopy(innovation, 0, current, 0, products);
            } else {
                for (int p = 0; p < products; p++) {
                    current[p] = rhoDays * previous[p] + innovationScale * innovation[p];
                }
            }

            for (int p = 0; p < products; p++) {
                samples[p][day] = toMultiplier(p, current[p]);
            }

            System.arraycopy(current, 0, previous, 0, products);
        }

        return samples;
    }

    private double[] correlatedGaussian(double[][] lower) {
        int dim = lower.length;
        double[] eps = new double[dim];
        for (int i = 0; i < dim; i++) {
            eps[i] = rng.nextGaussian();
        }

        double[] result = new double[dim];
        for (int i = 0; i < dim; i++) {
            double sum = 0.0;
            for (int k = 0; k <= i; k++) {
                sum += lower[i][k] * eps[k];
            }
            result[i] = sum;
        }

        return result;
    }

    private double toMultiplier(int productIndex, double normalValue) {
        double u = normalCdf(normalValue);
        double raw;
        switch (productIndex) {
            case 0:
                raw = minUni + u * (maxUni - minUni);
                break;
            case 1:
                raw = binomialFromUniform(u);
                break;
            case 2:
                raw = triangularFromUniform(u);
                break;
            default:
                throw new IllegalArgumentException("Unsupported product index: " + productIndex);
        }
        return applyMultipliers(raw);
    }

    private double binomialFromUniform(double u) {
        double prob = Math.pow(1.0 - p, n);
        double cdf = prob;
        int successes = 0;
        if (u <= cdf) {
            return successes / 10.0;
        }

        while (successes < (int) n) {
            prob = prob * (n - successes) / (successes + 1.0) * p / (1.0 - p);
            successes++;
            cdf += prob;
            if (u <= cdf) {
                break;
            }
        }

        return successes / 10.0;
    }

    private double triangularFromUniform(double u) {
        double c = (modeTri - minTri) / (maxTri - minTri);
        if (u < c) {
            return minTri + Math.sqrt(u * (maxTri - minTri) * (modeTri - minTri));
        } else {
            return maxTri - Math.sqrt((1.0 - u) * (maxTri - minTri) * (maxTri - modeTri));
        }
    }

    private double normalCdf(double value) {
        return 0.5 * (1.0 + erf(value / Math.sqrt(2.0)));
    }

    private double erf(double x) {
        double sign = x >= 0 ? 1 : -1;
        x = Math.abs(x);
        double t = 1.0 / (1.0 + 0.3275911 * x);
        double tau = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))));
        return sign * (1.0 - tau * Math.exp(-x * x));
    }

    private double[][] choleskyLower(double[][] matrix) {
        int dim = matrix.length;
        double[][] lower = new double[dim][dim];

        for (int i = 0; i < dim; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = 0.0;
                for (int k = 0; k < j; k++) {
                    sum += lower[i][k] * lower[j][k];
                }

                double value = matrix[i][j] - sum;
                if (i == j) {
                    lower[i][j] = Math.sqrt(Math.max(value, 1e-12));
                } else {
                    double pivot = lower[j][j];
                    if (pivot == 0.0) {
                        pivot = 1e-6;
                        lower[j][j] = pivot;
                    }
                    lower[i][j] = value / pivot;
                }
            }
        }

        return lower;
    }

}
