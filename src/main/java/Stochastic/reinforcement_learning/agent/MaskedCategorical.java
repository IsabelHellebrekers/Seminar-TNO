package Stochastic.reinforcement_learning.agent;

import java.util.Random;

public final class MaskedCategorical {
    private static final float NEG_INF = -1e9f;

    private final float[] probs;
    private final float[] logProbs;

    public MaskedCategorical(float[] logits, boolean[] legal) {
        if (logits.length != legal.length) throw new IllegalArgumentException("dim mismatch");

        float max = -Float.MAX_VALUE;
        for (int i = 0; i < logits.length; i++) {
            float z = legal[i] ? logits[i] : NEG_INF;
            if (z > max) max = z;
        }

        double sum = 0.0;
        double[] exps = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            if (!legal[i]) { exps[i] = 0; continue; }
            double e = Math.exp((double)(logits[i] - max));
            exps[i] = e;
            sum += e;
        }
        if (sum <= 0.0) throw new IllegalStateException("No legal actions.");

        probs = new float[logits.length];
        logProbs = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            double p = exps[i] / sum;
            probs[i] = (float) p;
            logProbs[i] = p > 0 ? (float)Math.log(p) : NEG_INF;
        }
    }

    public int sample(Random rng) {
        double r = rng.nextDouble();
        double c = 0.0;
        for (int i = 0; i < probs.length; i++) {
            c += probs[i];
            if (r <= c) return i;
        }
        // numerical edge case:
        for (int i = probs.length - 1; i >= 0; i--) if (probs[i] > 0) return i;
        return 0;
    }

    public float logProb(int action) { return logProbs[action]; }
    public float[] probs() { return probs.clone(); }
}