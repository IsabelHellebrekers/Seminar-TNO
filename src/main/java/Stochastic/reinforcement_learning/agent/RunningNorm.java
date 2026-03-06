package Stochastic.reinforcement_learning.agent;

public final class RunningNorm {
    private final int dim;
    private final double[] mean;
    private final double[] m2;
    private long n;

    private final float clipAbs; // e.g. 5

    public RunningNorm(int dim, float clipAbs) {
        this.dim = dim;
        this.mean = new double[dim];
        this.m2 = new double[dim];
        this.n = 0;
        this.clipAbs = clipAbs;
    }

    // Update with a single observation
    public void update(float[] x) {
        n++;
        for (int i = 0; i < dim; i++) {
            double xi = x[i];
            double delta = xi - mean[i];
            mean[i] += delta / n;
            double delta2 = xi - mean[i];
            m2[i] += delta * delta2;
        }
    }

    public float[] normalize(float[] x) {
        float[] y = x.clone();
        if (n < 2) return y;

        for (int i = 0; i < dim; i++) {
            double var = m2[i] / Math.max(1, (n - 1));
            double std = Math.sqrt(var) + 1e-8;

            double zi = (x[i] - mean[i]) / std;
            if (zi > clipAbs) zi = clipAbs;
            if (zi < -clipAbs) zi = -clipAbs;
            y[i] = (float) zi;
        }
        return y;
    }
}