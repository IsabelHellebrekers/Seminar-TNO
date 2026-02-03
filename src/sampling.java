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

    public static void main(String[] args) throws IOException {
        sampling x = new sampling();
        System.out.println(x.triangular());
    }
}
