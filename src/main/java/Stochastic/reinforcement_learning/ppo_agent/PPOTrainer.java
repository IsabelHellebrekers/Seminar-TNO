package Stochastic.reinforcement_learning.ppo_agent;

import DataUtils.InstanceCreator;
import Objects.Instance;
import Stochastic.reinforcement_learning.*;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PPOTrainer {

    private static final Logger LOG = Logger.getLogger(PPOTrainer.class.getName());

    // --- PPO hyperparams (good defaults) ---
    private static final double GAMMA = 0.99;
    private static final double LAMBDA = 0.95;

    private static final double LR = 3e-4;
    private static final double CLIP_EPS = 0.2;
    private static final double ENTROPY_COEF = 0.01;

    private static final int ROLLOUT_STEPS = 4096;       // total steps per update (across episodes)
    private static final int PPO_EPOCHS = 6;
    private static final int MINIBATCH_SIZE = 256;

    private static final double ADV_EPS = 1e-8;
    private static final boolean GREEDY_EVAL = false; // for training keep stochastic

    private record StepData(
            float[] obs,
            int action,
            double reward,
            boolean done,
            double valuePred,
            double oldLogProb,
            boolean[] legalMask
    ) {}

    public static void main(String[] args) throws Exception {

        LOG.setLevel(Level.INFO);
        Logger.getLogger(Environment.class.getName()).setLevel(Level.WARNING);

        Instance instance = InstanceCreator.createFDInstance().get(0);

        DemandModel demandModel = new DemandModel();
        ActionSpace actionSpace = new ActionSpace(instance);

        int mscTrucksPerDay = 60;
        int numFsc = instance.getFSCs().size();
        int[] fscTrucksPerDay = new int[numFsc];
        fscTrucksPerDay[0] = 40;
        fscTrucksPerDay[1] = 40;

        Environment env = new Environment(
                instance,
                actionSpace,
                demandModel,
                mscTrucksPerDay,
                fscTrucksPerDay
        );

        int horizon = env.getHorizon();

        State init = env.reset(123L);
        int obsSize = init.toObservationVector(horizon).length;

        long runSeed = System.nanoTime();
        LOG.info("RUN_SEED=" + runSeed);

        PPONetwork net = new PPONetwork(
                obsSize,
                actionSpace.size(),
                LR,
                CLIP_EPS,
                ENTROPY_COEF,
                runSeed
        );

        HeuristicGuidancePolicy heuristic = new HeuristicGuidancePolicy(instance, actionSpace);

        Random rng = new Random(runSeed);

        int updates = 50_000;
        int logEveryUpdates = 20;

        for (int update = 1; update <= updates; update++) {

            // --- Collect rollout batch ---
            List<StepData> rollout = collectRollout(env, net, heuristic, actionSpace, rng, ROLLOUT_STEPS);

            // --- Compute GAE advantages + returns ---
            GaeResult gae = computeGAE(rollout, GAMMA, LAMBDA);

            // --- PPO optimize ---
            ppoOptimize(net, rollout, gae);

            if (update % logEveryUpdates == 0) {
                double avgDays = evaluateAvgDays(env, net, actionSpace, rng, 50);
                LOG.info(String.format("UPDATE=%d | rolloutSteps=%d | evalAvgDays=%.2f (horizon=%d)",
                        update, rollout.size(), avgDays, horizon));
            }
        }
    }

    // =========================
    // Rollout collection
    // =========================

    private static List<StepData> collectRollout(Environment env,
                                                 PPONetwork net,
                                                 HeuristicGuidancePolicy heuristic,
                                                 ActionSpace actionSpace,
                                                 Random rng,
                                                 int targetSteps) {

        List<StepData> data = new ArrayList<>(targetSteps);

        while (data.size() < targetSteps) {

            env.reset(rng.nextLong());
            boolean done = false;

            while (!done && data.size() < targetSteps) {

                float[] obs = env.getCurrentObservationVector();
                State stateCopy = env.getCurrentStateCopy();
                boolean[] legal = env.getLegalActionMask();

                INDArray obsRow = Nd4j.create(obs).reshape(1, obs.length);
                INDArray[] out = net.forward(obsRow);

                INDArray logits = out[0]; // [1, actionSize]
                double value = out[1].getDouble(0);

                // --- Choose action ---
                int action;
                if (rng.nextDouble() < guidanceProb()) {
                    action = heuristic.selectAction(stateCopy, legal, actionSpace.getStopIndex(), rng);
                } else {
                    action = sampleMaskedCategorical(logits, legal, rng);
                }

                double oldLogProb = maskedLogProb(logits, legal, action);

                StepResult sr = env.step(action);

                data.add(new StepData(obs, action, sr.reward(), sr.done(), value, oldLogProb, legal));

                done = sr.done();
            }
        }

        return data;
    }

    private static double guidanceProb() {
        // IMPORTANT: don’t keep mixing heuristic forever.
        // Keep it small or set to 0 after you have enough stability.
        return 0.10;
    }

    // =========================
    // GAE
    // =========================

    private record GaeResult(double[] advantages, double[] returns) {}

    private static GaeResult computeGAE(List<StepData> rollout, double gamma, double lambda) {
        int T = rollout.size();
        double[] adv = new double[T];
        double[] ret = new double[T];

        double gae = 0.0;
        double nextValue = 0.0;

        for (int t = T - 1; t >= 0; t--) {
            StepData sd = rollout.get(t);

            double v = sd.valuePred;
            double nonTerminal = sd.done ? 0.0 : 1.0;

            double delta = sd.reward + gamma * nextValue * nonTerminal - v;
            gae = delta + gamma * lambda * gae * nonTerminal;

            adv[t] = gae;
            ret[t] = adv[t] + v;

            nextValue = v;
            if (sd.done) {
                nextValue = 0.0;
                gae = 0.0;
            }
        }

        // normalize advantages
        double mean = 0.0;
        for (double a : adv) mean += a;
        mean /= T;

        double var = 0.0;
        for (double a : adv) {
            double d = a - mean;
            var += d * d;
        }
        var /= T;
        double std = Math.sqrt(var) + ADV_EPS;

        for (int i = 0; i < T; i++) adv[i] = (adv[i] - mean) / std;

        return new GaeResult(adv, ret);
    }

    // =========================
    // PPO optimize
    // =========================

    private static void ppoOptimize(PPONetwork net, List<StepData> rollout, GaeResult gae) {
        int T = rollout.size();
        List<Integer> idx = new ArrayList<>(T);
        for (int i = 0; i < T; i++) idx.add(i);

        int obsSize = rollout.get(0).obs.length;
        int actionSize = net.getActionSize();

        for (int epoch = 0; epoch < PPO_EPOCHS; epoch++) {
            Collections.shuffle(idx);

            for (int start = 0; start < T; start += MINIBATCH_SIZE) {
                int end = Math.min(T, start + MINIBATCH_SIZE);
                int B = end - start;

                INDArray obsBatch = Nd4j.create(B, obsSize);
                INDArray policyLabels = Nd4j.zeros(B, actionSize + 2);
                INDArray valueLabels = Nd4j.create(B, 1);
                INDArray policyMask = Nd4j.zeros(B, actionSize);

                for (int bi = 0; bi < B; bi++) {
                    int t = idx.get(start + bi);
                    StepData sd = rollout.get(t);

                    // obs
                    for (int j = 0; j < obsSize; j++) obsBatch.putScalar(bi, j, sd.obs[j]);

                    // one-hot(action)
                    policyLabels.putScalar(bi, sd.action, 1.0);

                    // advantage + old log prob
                    policyLabels.putScalar(bi, actionSize, gae.advantages[t]);
                    policyLabels.putScalar(bi, actionSize + 1, sd.oldLogProb);

                    // value target
                    valueLabels.putScalar(bi, 0, gae.returns[t]);

                    // legal mask
                    for (int a = 0; a < actionSize; a++) {
                        policyMask.putScalar(bi, a, sd.legalMask[a] ? 1.0 : 0.0);
                    }
                }

                net.fit(obsBatch, policyLabels, valueLabels, policyMask);
            }
        }
    }

    // =========================
    // Masked categorical utils
    // =========================

    private static int sampleMaskedCategorical(INDArray logitsRow, boolean[] legal, Random rng) {
        double[] probs = maskedSoftmaxToArray(logitsRow, legal);

        // sample
        double r = rng.nextDouble();
        double cdf = 0.0;
        for (int a = 0; a < probs.length; a++) {
            cdf += probs[a];
            if (r <= cdf) return a;
        }
        return probs.length - 1;
    }

    private static double maskedLogProb(INDArray logitsRow, boolean[] legal, int action) {
        double[] probs = maskedSoftmaxToArray(logitsRow, legal);
        double p = Math.max(probs[action], 1e-12);
        return Math.log(p);
    }

    private static double[] maskedSoftmaxToArray(INDArray logitsRow, boolean[] legal) {
        int n = (int) logitsRow.size(1);
        double[] logits = new double[n];

        double max = -Double.MAX_VALUE;
        for (int a = 0; a < n; a++) {
            double z = legal[a] ? logitsRow.getDouble(0, a) : -1e9;
            logits[a] = z;
            if (z > max) max = z;
        }

        double sum = 0.0;
        double[] exp = new double[n];
        for (int a = 0; a < n; a++) {
            if (!legal[a]) {
                exp[a] = 0.0;
            } else {
                exp[a] = Math.exp(logits[a] - max);
                sum += exp[a];
            }
        }

        // If somehow nothing is legal, fallback uniform
        if (sum <= 0.0) {
            double[] p = new double[n];
            for (int a = 0; a < n; a++) p[a] = 1.0 / n;
            return p;
        }

        double[] p = new double[n];
        for (int a = 0; a < n; a++) p[a] = exp[a] / sum;
        return p;
    }

    // =========================
    // Quick evaluation
    // =========================

    private static double evaluateAvgDays(Environment env, PPONetwork net, ActionSpace actionSpace, Random rng, int episodes) {
        double sum = 0.0;
        for (int ep = 0; ep < episodes; ep++) {
            env.reset(rng.nextLong());
            boolean done = false;

            while (!done) {
                float[] obs = env.getCurrentObservationVector();
                boolean[] legal = env.getLegalActionMask();

                INDArray obsRow = Nd4j.create(obs).reshape(1, obs.length);
                INDArray[] out = net.forward(obsRow);
                INDArray logits = out[0];

                int action;
                if (GREEDY_EVAL) {
                    action = argmaxMasked(logits, legal, actionSpace.getStopIndex());
                } else {
                    action = sampleMaskedCategorical(logits, legal, rng);
                }

                StepResult sr = env.step(action);
                done = sr.done();
            }
            sum += env.getLastEpisodeDaysSurvived();
        }
        return sum / episodes;
    }

    private static int argmaxMasked(INDArray logitsRow, boolean[] legal, int fallback) {
        int n = (int) logitsRow.size(1);
        int best = -1;
        double bestVal = -Double.MAX_VALUE;
        for (int a = 0; a < n; a++) {
            if (!legal[a]) continue;
            double v = logitsRow.getDouble(0, a);
            if (v > bestVal) {
                bestVal = v;
                best = a;
            }
        }
        return best >= 0 ? best : fallback;
    }
}