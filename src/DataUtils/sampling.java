package DataUtils;

import java.io.IOException;
import java.util.Random;

public final class sampling {

    private final Random rng;

    private final double minUni = 0.8;
    private final double maxUni = 1.2;
    private final double n = 25;
    private final double p = 0.4;
    private final double minTri = 0.2;
    private final double maxTri = 2.0;
    private final double modeTri = 0.8;

    public sampling() {
        this.rng = new Random(); // non-deterministic seed
    }

    /**
     * Uniform(min, max)
     */
    public double uniform() {
        return minUni + rng.nextDouble() * (maxUni - minUni);
    }

    public long[] stochasticFW(int dailyFoodWaterKg) {
        long[] FW = new long[10];
        for (int i = 0; i < FW.length; i++) {
            FW[i] = (long) (dailyFoodWaterKg * uniform());
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

    public long[] stochasticFUEL(int dailyFuelKg) {
        long[] FUEL = new long[10];
        for (int i = 0; i < FUEL.length; i++) {
            FUEL[i] = (long) (dailyFuelKg * binomial());
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

    public long[] stochasticAMMO(int dailyAmmoKg) {
        long[] AMMO = new long[10];
        for (int i = 0; i < AMMO.length; i++) {
            AMMO[i] = (long) (dailyAmmoKg * triangular());
        }

        return AMMO;
    }
    public double uniformQuantile(double epsilon) {
        double alpha = 1.0 - epsilon;

        return minUni + alpha * (maxUni - minUni);
    }

    public double binomialQuantile(double epsilon) {
        double alpha = 1.0 - epsilon;
        if (alpha <= 0.0) return 0;
        if (alpha >= 1.0) return n / 10;

        // P(X = 0)
        double pmf = Math.pow(1.0 - p, n);
        double cdf = pmf;

        for (int k = 0; k < n; k++) {
            if (cdf >= alpha) {
                return (double) k / 10;
            } // if
            // Recurrence P(X = k+1)
            pmf *= (n-k) / (k + 1) * p / (1.0 - p);
            cdf += pmf;
        } // for k
        return n / 10;
    }

    public double triangularQuantile(double epsilon) {
        double alpha = 1.0 - epsilon;

        double fc = (modeTri - minTri) / (maxTri - minTri);

        if (alpha <= fc) {
            return minTri + Math.sqrt(alpha * (maxTri - minTri) * (modeTri - minTri));
        } // if
        else {
            return maxTri - Math.sqrt(epsilon * (maxTri - minTri) * (maxTri - modeTri));
        } // else
    }

    public static void main(String[] args) throws IOException {
        sampling x = new sampling();
        System.out.println(x.triangularQuantile(0.05/330));
    }
}
