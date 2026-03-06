package Stochastic.reinforcement_learning.agent;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

public final class ActorCritic {

    // ---- Hyperparams ----
    private final int obsDim, actDim, hidden;
    private final float lrActor, lrCritic;

    // ---- Actor params ----
    private final float[] Wa, ba; // obs->hidden
    private final float[] Ua, ca; // hidden->actDim

    // ---- Critic params ----
    private final float[] Wc, bc; // obs->hidden
    private final float[] Uc, cc; // hidden->1

    // ---- Adam state (actor) ----
    private final AdamState adamWa, adamba, adamUa, adamca;
    // ---- Adam state (critic) ----
    private final AdamState adamWc, adambc, adamUc, adamcc;

    private int t = 0;

    public ActorCritic(int obsDim, int actDim, int hidden, float lrActor, float lrCritic, Random rng) {
        this.obsDim = obsDim;
        this.actDim = actDim;
        this.hidden = hidden;
        this.lrActor = lrActor;
        this.lrCritic = lrCritic;

        Wa = new float[obsDim * hidden];
        ba = new float[hidden];
        Ua = new float[hidden * actDim];
        ca = new float[actDim];

        Wc = new float[obsDim * hidden];
        bc = new float[hidden];
        Uc = new float[hidden * 1];
        cc = new float[1];

        xavierInit(Wa, obsDim, hidden, rng);
        xavierInit(Ua, hidden, actDim, rng);
        xavierInit(Wc, obsDim, hidden, rng);
        xavierInit(Uc, hidden, 1, rng);

        adamWa = new AdamState(Wa.length);
        adamba = new AdamState(ba.length);
        adamUa = new AdamState(Ua.length);
        adamca = new AdamState(ca.length);

        adamWc = new AdamState(Wc.length);
        adambc = new AdamState(bc.length);
        adamUc = new AdamState(Uc.length);
        adamcc = new AdamState(cc.length);
    }

    // Forward actor -> logits
    public float[] policyLogits(float[] obs) {
        float[] h = new float[hidden];
        // h = relu(obs*Wa + ba)
        for (int j = 0; j < hidden; j++) {
            float s = ba[j];
            for (int i = 0; i < obsDim; i++) s += obs[i] * Wa[i * hidden + j];
            h[j] = s > 0 ? s : 0;
        }
        float[] logits = new float[actDim];
        for (int a = 0; a < actDim; a++) {
            float s = ca[a];
            for (int j = 0; j < hidden; j++) s += h[j] * Ua[j * actDim + a];
            logits[a] = s;
        }
        return logits;
    }

    // Forward critic -> value
    public float value(float[] obs) {
        float[] h = new float[hidden];
        for (int j = 0; j < hidden; j++) {
            float s = bc[j];
            for (int i = 0; i < obsDim; i++) s += obs[i] * Wc[i * hidden + j];
            h[j] = s > 0 ? s : 0;
        }
        float s = cc[0];
        for (int j = 0; j < hidden; j++) s += h[j] * Uc[j];
        return s;
    }

    /**
     * One PPO minibatch SGD step for actor + critic.
     * Inputs are one sample at a time to keep code minimal.
     *
     * Actor gradient is provided as dL/dlogits (length actDim).
     * Critic gradient is dL/dvalue (scalar).
     */
    public void backwardAndStep(float[] obs, float[] dLogits, float dValue) {
        t++;

        // ---- ACTOR BACKPROP ----
        // Forward caches
        float[] za = new float[hidden];
        float[] ha = new float[hidden];
        for (int j = 0; j < hidden; j++) {
            float s = ba[j];
            for (int i = 0; i < obsDim; i++) s += obs[i] * Wa[i * hidden + j];
            za[j] = s;
            ha[j] = s > 0 ? s : 0;
        }

        // grads on Ua, ca
        float[] gUa = new float[Ua.length];
        float[] gca = new float[ca.length];
        float[] dha = new float[hidden];

        for (int a = 0; a < actDim; a++) {
            gca[a] += dLogits[a];
            for (int j = 0; j < hidden; j++) {
                gUa[j * actDim + a] += ha[j] * dLogits[a];
                dha[j] += Ua[j * actDim + a] * dLogits[a];
            }
        }

        // back through relu
        float[] dza = new float[hidden];
        for (int j = 0; j < hidden; j++) dza[j] = (za[j] > 0 ? 1f : 0f) * dha[j];

        // grads on Wa, ba
        float[] gWa = new float[Wa.length];
        float[] gba = new float[ba.length];
        for (int j = 0; j < hidden; j++) {
            gba[j] += dza[j];
            for (int i = 0; i < obsDim; i++) gWa[i * hidden + j] += obs[i] * dza[j];
        }

        adamUpdate(Wa, gWa, adamWa, lrActor);
        adamUpdate(ba, gba, adamba, lrActor);
        adamUpdate(Ua, gUa, adamUa, lrActor);
        adamUpdate(ca, gca, adamca, lrActor);

        // ---- CRITIC BACKPROP ----
        float[] zc = new float[hidden];
        float[] hc = new float[hidden];
        for (int j = 0; j < hidden; j++) {
            float s = bc[j];
            for (int i = 0; i < obsDim; i++) s += obs[i] * Wc[i * hidden + j];
            zc[j] = s;
            hc[j] = s > 0 ? s : 0;
        }

        // value = cc + sum_j hc[j]*Uc[j]
        float[] gUc = new float[Uc.length];
        float[] gcc = new float[1];
        float[] dhc = new float[hidden];

        gcc[0] += dValue;
        for (int j = 0; j < hidden; j++) {
            gUc[j] += hc[j] * dValue;
            dhc[j] += Uc[j] * dValue;
        }

        float[] dzc = new float[hidden];
        for (int j = 0; j < hidden; j++) dzc[j] = (zc[j] > 0 ? 1f : 0f) * dhc[j];

        float[] gWc = new float[Wc.length];
        float[] gbc = new float[bc.length];
        for (int j = 0; j < hidden; j++) {
            gbc[j] += dzc[j];
            for (int i = 0; i < obsDim; i++) gWc[i * hidden + j] += obs[i] * dzc[j];
        }

        adamUpdate(Wc, gWc, adamWc, lrCritic);
        adamUpdate(bc, gbc, adambc, lrCritic);
        adamUpdate(Uc, gUc, adamUc, lrCritic);
        adamUpdate(cc, gcc, adamcc, lrCritic);
    }

    // ---- Adam helpers (minimal) ----
    private static final class AdamState {
        final float[] m, v;
        AdamState(int n) { m = new float[n]; v = new float[n]; }
    }

    private void adamUpdate(float[] w, float[] g, AdamState st, float lr) {
        float b1 = 0.9f, b2 = 0.999f, eps = 1e-8f;
        float b1t = (float)(1.0 - Math.pow(b1, t));
        float b2t = (float)(1.0 - Math.pow(b2, t));

        for (int i = 0; i < w.length; i++) {
            float gi = g[i];
            st.m[i] = b1 * st.m[i] + (1f - b1) * gi;
            st.v[i] = b2 * st.v[i] + (1f - b2) * gi * gi;

            float mh = st.m[i] / b1t;
            float vh = st.v[i] / b2t;

            w[i] -= lr * mh / ((float)Math.sqrt(vh) + eps);
        }
    }

    private static void xavierInit(float[] w, int fanIn, int fanOut, Random rng) {
        double lim = Math.sqrt(6.0 / (fanIn + fanOut));
        for (int i = 0; i < w.length; i++) {
            w[i] = (float)((rng.nextDouble() * 2 - 1) * lim);
        }
    }

    public void saveSnapshot(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
            out.writeInt(obsDim);
            out.writeInt(actDim);
            out.writeInt(hidden);
            out.writeInt(t);

            writeArray(out, Wa);
            writeArray(out, ba);
            writeArray(out, Ua);
            writeArray(out, ca);

            writeArray(out, Wc);
            writeArray(out, bc);
            writeArray(out, Uc);
            writeArray(out, cc);
        }
    }

    private static void writeArray(DataOutputStream out, float[] arr) throws IOException {
        out.writeInt(arr.length);
        for (float v : arr) {
            out.writeFloat(v);
        }
    }
}
