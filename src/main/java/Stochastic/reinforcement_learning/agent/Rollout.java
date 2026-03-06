package Stochastic.reinforcement_learning.agent;

import java.util.ArrayList;
import java.util.List;

public final class Rollout {
    public static final class Step {
        public final float[] obs;
        public final boolean[] mask;
        public final int action;
        public final int heuristicAction;
        public final float oldLogp;
        public final float value;
        public final float reward;
        public final boolean done;

        public float advantage;
        public float ret;

        Step(float[] obs, boolean[] mask, int action, int heuristicAction, float oldLogp, float value, float reward, boolean done) {
            this.obs = obs;
            this.mask = mask;
            this.action = action;
            this.heuristicAction = heuristicAction;
            this.oldLogp = oldLogp;
            this.value = value;
            this.reward = reward;
            this.done = done;
        }
    }

    private final List<Step> steps = new ArrayList<>();

    public void add(float[] obs, boolean[] mask, int action, int heuristicAction, float oldLogp, float value, float reward, boolean done) {
        steps.add(new Step(obs, mask.clone(), action, heuristicAction, oldLogp, value, reward, done));
    }

    public List<Step> steps() { return steps; }
    public int size() { return steps.size(); }

    public void computeGAE(float lastValue, float gamma, float lam) {
        float gae = 0f;

        for (int t = steps.size() - 1; t >= 0; t--) {
            Step s = steps.get(t);
            float nextV = (t == steps.size() - 1) ? lastValue : steps.get(t + 1).value;
            float nonTerminal = s.done ? 0f : 1f;

            float delta = s.reward + gamma * nextV * nonTerminal - s.value;
            gae = delta + gamma * lam * nonTerminal * gae;

            s.advantage = gae;
            s.ret = s.advantage + s.value;
        }

        normalizeAdvantages();
    }

    private void normalizeAdvantages() {
        int n = steps.size();
        if (n == 0) return;

        double mean = 0.0;
        for (Step s : steps) mean += s.advantage;
        mean /= n;

        double var = 0.0;
        for (Step s : steps) {
            double d = s.advantage - mean;
            var += d * d;
        }
        var /= Math.max(1, n - 1);
        double std = Math.sqrt(var) + 1e-8;

        for (Step s : steps) s.advantage = (float)((s.advantage - mean) / std);
    }
}
