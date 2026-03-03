package Stochastic.reinforcement_learning.dqn_agent;

import DataUtils.InstanceCreator;
import Objects.Instance;
import Stochastic.reinforcement_learning.*;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MaskedDoubleDqnTrainer {

    private static final Logger LOG = Logger.getLogger(MaskedDoubleDqnTrainer.class.getName());

    private static final double GAMMA = 0.995;
    private static final double LR = 3e-4;
    private static final int BATCH_SIZE = 128;
    private static final int REPLAY_CAPACITY = 250_000;
    private static final int TRAIN_START_SIZE = 5_000;
    private static final int TRAIN_EVERY_STEPS = 4;
    private static final double TARGET_SOFT_TAU = 0.005;
    private static final double REWARD_CLIP = 10.0;

    private static final double EPS_START = 1.0;
    private static final double EPS_END = 0.15;
    private static final int EPS_DECAY_STEPS = 1_200_000;
    private static final double HEURISTIC_GUIDANCE_START = 0.75;
    private static final double HEURISTIC_GUIDANCE_END = 0.10;
    private static final int HEURISTIC_GUIDANCE_DECAY_STEPS = 900_000;

    private record Transition(float[] obs,
                              int action,
                              double reward,
                              float[] nextObs,
                              boolean done,
                              boolean[] nextMask) {
    }

    private static final class ReplayBuffer {
        private final Transition[] data;
        private int size = 0;
        private int ptr = 0;

        ReplayBuffer(int capacity) {
            this.data = new Transition[capacity];
        }

        void add(Transition t) {
            data[ptr] = t;
            ptr = (ptr + 1) % data.length;
            if (size < data.length) {
                size++;
            }
        }

        int size() {
            return size;
        }

        List<Transition> sample(int batchSize, Random rng) {
            List<Transition> out = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) {
                int idx = rng.nextInt(size);
                out.add(data[idx]);
            }
            return out;
        }
    }

    public static void main(String[] args) throws Exception {
        LOG.setLevel(Level.INFO);
        Logger.getLogger(Environment.class.getName()).setLevel(Level.WARNING);

        Instance instance = InstanceCreator.createFDInstance().get(0);

        DemandModel demandModel = new DemandModel();
        ActionSpace actionSpace = new ActionSpace(instance);

        int mscTrucksPerDay = 60;
        int[] fscTrucksPerDay = new int[instance.getFSCs().size()];
        fscTrucksPerDay[0] = 40;
        fscTrucksPerDay[1] = 40;

        Environment env = new Environment(
                instance,
                actionSpace,
                demandModel,
                mscTrucksPerDay,
                fscTrucksPerDay,
                false
        );

        int horizon = env.getHorizon();
        State init = env.reset(123L);
        int obsSize = init.toObservationVector(horizon).length;

        long runSeed = System.nanoTime();
        LOG.info("RUN_SEED=" + runSeed);
        Random rng = new Random(runSeed);

        MaskedDqnNetwork online = new MaskedDqnNetwork(obsSize, actionSpace.size(), LR, runSeed);
        MaskedDqnNetwork target = online.copy();
        HeuristicGuidancePolicy heuristicPolicy = new HeuristicGuidancePolicy(instance, actionSpace);

        ReplayBuffer replay = new ReplayBuffer(REPLAY_CAPACITY);

        int episodes = 200_000;
        int maxStepsPerEpisode = 50_000;
        int logEveryEpisodes = 250;
        int metricsWindow = 200;
        int globalStep = 0;

        int bestDaysSurvived = 0;
        int lastImprovementEpisode = 0;

        int[] windowDays = new int[metricsWindow];
        double[] windowRewards = new double[metricsWindow];
        double[] windowStockoutKg = new double[metricsWindow];
        int[] windowSteps = new int[metricsWindow];
        int windowPtr = 0;
        int windowCount = 0;

        for (int ep = 1; ep <= episodes; ep++) {
            env.reset(rng.nextLong());

            boolean done = false;
            double episodeReward = 0.0;
            int stepCount = 0;

            while (!done && stepCount < maxStepsPerEpisode) {
                float[] obs = env.getCurrentObservationVector();
                State state = env.getCurrentStateCopy();
                boolean[] mask = env.getLegalActionMask();

                double epsilon = epsilonAt(globalStep);
                int action = selectActionEpsilonGreedyMasked(
                        online,
                        heuristicPolicy,
                        state,
                        obs,
                        mask,
                        actionSpace.getStopIndex(),
                        epsilon,
                        rng,
                        globalStep
                );

                StepResult sr = env.step(action);
                float[] nextObs = sr.nextState().toObservationVector(horizon);
                boolean[] nextMask = sr.done() ? null : env.getLegalActionMask();

                double clippedReward = clip(sr.reward(), -REWARD_CLIP, REWARD_CLIP);
                replay.add(new Transition(obs, action, clippedReward, nextObs, sr.done(), nextMask));

                episodeReward += sr.reward();
                done = sr.done();
                stepCount++;
                globalStep++;

                if (replay.size() >= TRAIN_START_SIZE && globalStep % TRAIN_EVERY_STEPS == 0) {
                    trainStep(online, target, replay, rng);
                }

                target.softUpdateFrom(online, TARGET_SOFT_TAU);
            }

            double stockoutKg = env.getLastEpisodeTotalStockoutKg();
            int daysSurvived = env.getLastEpisodeDaysSurvived();

            if (daysSurvived > bestDaysSurvived) {
                bestDaysSurvived = daysSurvived;
                lastImprovementEpisode = ep;
                LOG.info(String.format(
                        "NEW_BEST | EP=%d | days=%d | reward=%.3f | steps=%d | stockoutKg=%.3f | epsilon=%.4f",
                        ep, daysSurvived, episodeReward, stepCount, stockoutKg, epsilonAt(globalStep)
                ));
            }

            windowDays[windowPtr] = daysSurvived;
            windowRewards[windowPtr] = episodeReward;
            windowStockoutKg[windowPtr] = stockoutKg;
            windowSteps[windowPtr] = stepCount;
            windowPtr = (windowPtr + 1) % metricsWindow;
            if (windowCount < metricsWindow) {
                windowCount++;
            }

            if (ep % logEveryEpisodes == 0 || stockoutKg == 0.0) {
                double avgDays = 0.0;
                double avgReward = 0.0;
                double avgStockoutKg = 0.0;
                double avgSteps = 0.0;
                int minDays = Integer.MAX_VALUE;
                int maxDays = Integer.MIN_VALUE;
                int successCount = 0;
                int highDayCount = 0;

                for (int i = 0; i < windowCount; i++) {
                    int d = windowDays[i];
                    avgDays += d;
                    avgReward += windowRewards[i];
                    avgStockoutKg += windowStockoutKg[i];
                    avgSteps += windowSteps[i];
                    if (d < minDays) minDays = d;
                    if (d > maxDays) maxDays = d;
                    if (d >= horizon) successCount++;
                    if (d >= Math.min(6, horizon)) highDayCount++;
                }
                avgDays /= windowCount;
                avgReward /= windowCount;
                avgStockoutKg /= windowCount;
                avgSteps /= windowCount;

                double dayVar = 0.0;
                for (int i = 0; i < windowCount; i++) {
                    double diff = windowDays[i] - avgDays;
                    dayVar += diff * diff;
                }
                double dayStd = Math.sqrt(dayVar / windowCount);

                LOG.info(String.format(
                        "SUMMARY | EP=%d | step=%d | epsilon=%.4f | heur=%.3f | replay=%d | " +
                                "current(days=%d,reward=%.3f,steps=%d,stockoutKg=%.3f) | " +
                                "window[%d](avgDays=%.2f,stdDays=%.2f,minDays=%d,maxDays=%d,avgReward=%.3f,avgStockoutKg=%.1f,avgSteps=%.1f,successRate=%.3f,day>=6Rate=%.3f) | " +
                                "bestDays=%d | noBestFor=%d eps",
                        ep,
                        globalStep,
                        epsilonAt(globalStep),
                        guidanceProbAt(globalStep),
                        replay.size(),
                        daysSurvived,
                        episodeReward,
                        stepCount,
                        stockoutKg,
                        windowCount,
                        avgDays,
                        dayStd,
                        minDays,
                        maxDays,
                        avgReward,
                        avgStockoutKg,
                        avgSteps,
                        (double) successCount / windowCount,
                        (double) highDayCount / windowCount,
                        bestDaysSurvived,
                        ep - lastImprovementEpisode
                ));
            }
        }
    }

    private static void trainStep(MaskedDqnNetwork online,
                                  MaskedDqnNetwork target,
                                  ReplayBuffer replay,
                                  Random rng) {

        List<Transition> batch = replay.sample(BATCH_SIZE, rng);

        int obsSize = online.getObsSize();
        int actionSize = online.getActionSize();
        int batchSize = batch.size();

        INDArray obsBatch = Nd4j.create(batchSize, obsSize);
        INDArray nextObsBatch = Nd4j.create(batchSize, obsSize);
        int[] actions = new int[batchSize];
        double[] rewards = new double[batchSize];
        boolean[] dones = new boolean[batchSize];
        boolean[][] nextMasks = new boolean[batchSize][];

        for (int i = 0; i < batchSize; i++) {
            Transition t = batch.get(i);
            for (int j = 0; j < obsSize; j++) {
                obsBatch.putScalar(i, j, t.obs[j]);
                nextObsBatch.putScalar(i, j, t.nextObs[j]);
            }
            actions[i] = t.action;
            rewards[i] = t.reward;
            dones[i] = t.done;
            nextMasks[i] = t.nextMask;
        }

        INDArray currentQ = online.qValues(obsBatch);
        INDArray nextOnlineQ = online.qValues(nextObsBatch);
        INDArray nextTargetQ = target.qValues(nextObsBatch);

        INDArray targetQ = currentQ.dup();

        for (int i = 0; i < batchSize; i++) {
            double tdTarget = rewards[i];

            if (!dones[i]) {
                int nextBestAction = argmaxMasked(nextOnlineQ, i, nextMasks[i]);
                double nextQ = nextTargetQ.getDouble(i, nextBestAction);
                tdTarget += GAMMA * nextQ;
            }

            targetQ.putScalar(i, actions[i], tdTarget);
        }

        online.fit(obsBatch, targetQ);
    }

    private static int selectActionEpsilonGreedyMasked(MaskedDqnNetwork net,
                                                       HeuristicGuidancePolicy heuristicPolicy,
                                                       State state,
                                                       float[] obs,
                                                       boolean[] mask,
                                                       int stopIndex,
                                                       double epsilon,
                                                       Random rng,
                                                       int globalStep) {
        if (rng.nextDouble() < epsilon) {
            if (rng.nextDouble() < guidanceProbAt(globalStep)) {
                return heuristicPolicy.selectAction(state, mask, stopIndex, rng);
            }
            return randomLegalAction(mask, stopIndex, rng);
        }

        INDArray obsRow = Nd4j.create(obs).reshape(1, obs.length);
        INDArray q = net.qValues(obsRow);
        return argmaxMasked(q, 0, mask);
    }

    private static int argmaxMasked(INDArray qValues, int row, boolean[] mask) {
        double best = Double.NEGATIVE_INFINITY;
        int bestAction = -1;

        for (int a = 0; a < mask.length; a++) {
            if (!mask[a]) {
                continue;
            }
            double qa = qValues.getDouble(row, a);
            if (qa > best) {
                best = qa;
                bestAction = a;
            }
        }

        if (bestAction < 0) {
            throw new IllegalStateException("No legal action available for masked argmax.");
        }
        return bestAction;
    }

    private static int randomLegalAction(boolean[] mask, int stopIndex, Random rng) {
        int legalCount = 0;
        for (boolean legal : mask) {
            if (legal) legalCount++;
        }

        if (legalCount == 0) {
            return stopIndex;
        }

        int pick = rng.nextInt(legalCount);
        for (int i = 0; i < mask.length; i++) {
            if (mask[i]) {
                if (pick == 0) return i;
                pick--;
            }
        }
        return stopIndex;
    }

    private static double epsilonAt(int globalStep) {
        if (globalStep >= EPS_DECAY_STEPS) {
            return EPS_END;
        }
        double frac = (double) globalStep / EPS_DECAY_STEPS;
        return EPS_START + frac * (EPS_END - EPS_START);
    }

    private static double guidanceProbAt(int globalStep) {
        if (globalStep >= HEURISTIC_GUIDANCE_DECAY_STEPS) {
            return HEURISTIC_GUIDANCE_END;
        }
        double frac = (double) globalStep / HEURISTIC_GUIDANCE_DECAY_STEPS;
        return HEURISTIC_GUIDANCE_START + frac * (HEURISTIC_GUIDANCE_END - HEURISTIC_GUIDANCE_START);
    }

    private static double clip(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }
}
