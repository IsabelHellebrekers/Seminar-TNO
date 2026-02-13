package Stochastic;
import java.io.IOException;
import java.util.Random;

public final class Sampling {

    private final Random rng;

    private final double minUni = 0.8;
    private final double maxUni = 1.2;
    private final double n = 25;
    private final double p = 0.4;
    private final double minTri = 0.2;
    private final double maxTri = 2.0;
    private final double modeTri = 0.8;

    public Sampling() {
        this.rng = new Random(); // non-deterministic seed
    }

    /**
     * Uniform(min, max)
     */
    public double uniform() {
        return minUni + rng.nextDouble() * (maxUni - minUni);
    }

    public double uniformQuantile(double epsilon) {
        double alpha = 1.0 - epsilon;
        return minUni + alpha * (maxUni - minUni);
    }

    public double[] stochasticFW(int dailyFoodWaterKg) {
        double[] FW = new double[10];
        for (int i = 0; i < FW.length; i++) {
            FW[i] = (dailyFoodWaterKg * uniform());
        }

        return FW;
    }

    /**
     * Binomial(n, p) via n Bernoulli trials
     */
    public double binomial() {
        int successes = 0;
        for (int i = 0; i < n; i++) {
            if (rng.nextDouble() < p) successes++;
        }
        return successes*1.0/10;
    }

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

    public double[] stochasticFUEL(int dailyFuelKg) {
        double[] FUEL = new double[10];
        for (int i = 0; i < FUEL.length; i++) {
            FUEL[i] = (dailyFuelKg * binomial());
        }

        return FUEL;
    }

    /**
     * Triangular(min, mode, max) via inverse CDF.
     * Requires: min <= mode <= max
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

    public double triangularQuantile(double epsilon) {
        double alpha = 1.0 - epsilon;
        if (alpha <= (modeTri - minTri) / (maxTri - minTri)) {
            return minTri + Math.sqrt((maxTri - minTri) * (modeTri - minTri));
        }
        else {
            return maxTri - Math.sqrt(epsilon * (maxTri - minTri) * (maxTri - modeTri));
        }
    }

    public double[] stochasticAMMO(int dailyAmmoKg) {
        double[] AMMO = new double[10];
        for (int i = 0; i < AMMO.length; i++) {
            AMMO[i] = (dailyAmmoKg * triangular());
        }

        return AMMO;
    }

    public static void main(String[] args) throws IOException {
        Sampling x = new Sampling();
        System.out.println(x.triangular());
    }
}
