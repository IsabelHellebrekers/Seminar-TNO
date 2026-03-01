package Stochastic.reinforcement_learning.actor_critic_agent;

import DataUtils.InstanceCreator;
import Objects.Instance;
import Stochastic.reinforcement_learning.*;
import Stochastic.reinforcement_learning.dqn_agent.HeuristicGuidancePolicy;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ActorCriticTrainer {

    private static final Logger LOG = Logger.getLogger(ActorCriticTrainer.class.getName());

    // Hyperparameters
    private static final double GAMMA = 0.99;
    private static final double LR = 1e-1;
    private static final int UPDATE_EVERY_EPISODES = 16;
    private static final int TEMP_WARMUP_EPISODES = 200;
    private static final int TEMP_DECAY_EPISODES = 10_000;
    private static final double START_TEMPERATURE = 1.0;
    private static final double END_TEMPERATURE = 0.10;
    private static final double HEURISTIC_GUIDANCE_START = 0.75;
    private static final double HEURISTIC_GUIDANCE_END = 0.10;
    private static final int HEURISTIC_GUIDANCE_DECAY_EPISODES = 12_000;

    // If true, choose argmax instead of sampling
    private static final boolean GREEDY_ACTIONS = false;

    private record StepData(float[] obs,
                            int action,
                            double reward,
                            boolean done,
                            double valuePred,
                            boolean[] mask) {}

    public static void main(String[] args) throws Exception {

        LOG.setLevel(Level.INFO);
        Logger.getLogger(Environment.class.getName()).setLevel(Level.WARNING);

        // TODO: change instance
        Instance instance = InstanceCreator.createFDInstance().get(0);

        DemandModel demandModel = new DemandModel();
        ActionSpace actionSpace = new ActionSpace(instance);

        int mscTrucksPerDay = 60;

        int numFsc = instance.getFSCs().size();
        int[] fscTrucksPerDay = new int[numFsc];

        // TODO: fill with your numbers per FSC
        fscTrucksPerDay[0] = 40;
        fscTrucksPerDay[1] = 40;

        Environment env = new Environment(
                instance,
                actionSpace,
                demandModel,
                mscTrucksPerDay,
                fscTrucksPerDay);

        int horizon = env.getHorizon();

        // Determine observation size
        State init = env.reset(123L);
        int obsSize = init.toObservationVector(horizon).length;

        // TODO: change to consistent seed for reproducability
        long runSeed = System.nanoTime();
        LOG.info("RUN_SEED=" + runSeed);

        ActorCriticNetwork net = new ActorCriticNetwork(obsSize, actionSpace.size(), LR, runSeed);
        HeuristicGuidancePolicy heuristicPolicy = new HeuristicGuidancePolicy(instance, actionSpace);

        int episodes = 200_000;
        int maxStepsPerEpisode = 50_000;
        int logEvery = 500;
        int metricsWindow = 200;
        int bestDaysSurvived = 0;
        int lastImprovementEpisode = 0;

        Random rng = new Random(runSeed);
        List<List<StepData>> pendingTrajectories = new ArrayList<>();

        int[] windowDays = new int[metricsWindow];
        double[] windowRewards = new double[metricsWindow];
        double[] windowStockoutKg = new double[metricsWindow];
        int[] windowSteps = new int[metricsWindow];
        int windowPtr = 0;
        int windowCount = 0;

        for (int ep = 1; ep <= episodes; ep++) {

            // TODO: change to rng.nextLong() to get stochastic demands
            // 123L is een goede seed aangezien die bij een slechte policy 1 stockout op day 2 krijgt
            env.reset(rng.nextLong());
            List<StepData> traj = new ArrayList<>();

            boolean done = false;
            double episodeReward = 0.0;
            int stepCount = 0;

            while (!done) {

                float[] obs = env.getCurrentObservationVector();
                State state = env.getCurrentStateCopy();
                boolean[] mask = env.getLegalActionMask();

                INDArray obsRow = Nd4j.create(obs).reshape(1, obs.length);
                INDArray[] out = net.forward(obsRow);

                INDArray policyProbs = out[0]; // [1, actionSize]
                double valuePred = out[1].getDouble(0);

                double temperature = getExplorationTemperature(ep);
                int action = selectActionMasked(
                        heuristicPolicy,
                        state,
                        policyProbs,
                        mask,
                        actionSpace.getStopIndex(),
                        rng,
                        GREEDY_ACTIONS,
                        temperature,
                        ep);

                StepResult sr = env.step(action);

                traj.add(new StepData(obs, action, sr.reward(), sr.done(), valuePred, mask));

                episodeReward += sr.reward();
                done = sr.done();
                stepCount++;

                // Optional safety
                if (stepCount > maxStepsPerEpisode) {
                    LOG.warning("Episode too long; terminating.");
                    break;
                }
            }

            // Train on a small batch of complete trajectories to reduce update variance.
            pendingTrajectories.add(traj);
            if (pendingTrajectories.size() >= UPDATE_EVERY_EPISODES) {
                trainOnTrajectoryBatch(net, pendingTrajectories);
                pendingTrajectories.clear();
            }

            // Logging you asked for: track stockouts and success episodes
            double stockoutKg = env.getLastEpisodeTotalStockoutKg();
            int daysSurvived = env.getLastEpisodeDaysSurvived();

            boolean improved = daysSurvived > bestDaysSurvived;
            if (improved) {
                bestDaysSurvived = daysSurvived;
                lastImprovementEpisode = ep;
                LOG.info(String.format(
                        "NEW_BEST | EP=%d | days=%d | reward=%.3f | steps=%d | stockoutKg=%.3f | temp=%.3f",
                        ep, daysSurvived, episodeReward, stepCount, stockoutKg, getExplorationTemperature(ep)
                ));
            }

            // Update rolling window metrics.
            windowDays[windowPtr] = daysSurvived;
            windowRewards[windowPtr] = episodeReward;
            windowStockoutKg[windowPtr] = stockoutKg;
            windowSteps[windowPtr] = stepCount;
            windowPtr = (windowPtr + 1) % metricsWindow;
            if (windowCount < metricsWindow) {
                windowCount++;
            }

            if (ep % logEvery == 0 || stockoutKg == 0.0) {
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

                int episodesSinceBest = ep - lastImprovementEpisode;
                LOG.info(String.format(
                        "SUMMARY | EP=%d | temp=%.3f | heur=%.3f | pendingBatch=%d/%d | current(days=%d,reward=%.3f,steps=%d,stockoutKg=%.3f) | " +
                                "window[%d](avgDays=%.2f,stdDays=%.2f,minDays=%d,maxDays=%d,avgReward=%.3f,avgStockoutKg=%.1f,avgSteps=%.1f,successRate=%.3f,day>=6Rate=%.3f) | " +
                                "bestDays=%d | noBestFor=%d eps",
                        ep,
                        getExplorationTemperature(ep),
                        guidanceProbAtEpisode(ep),
                        pendingTrajectories.size(),
                        UPDATE_EVERY_EPISODES,
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
                        episodesSinceBest
                ));
            }
        }

        // Final flush when episode count is not divisible by UPDATE_EVERY_EPISODES.
        if (!pendingTrajectories.isEmpty()) {
            trainOnTrajectoryBatch(net, pendingTrajectories);
        }
    }

    private static int selectActionMasked(HeuristicGuidancePolicy heuristicPolicy,
                                          State state,
                                          INDArray policyProbsRow,
                                          boolean[] mask,
                                          int stopIndex,
                                          Random rng,
                                          boolean greedy,
                                          double temperature,
                                          int episode) {
        if (!greedy && rng.nextDouble() < guidanceProbAtEpisode(episode)) {
            return heuristicPolicy.selectAction(state, mask, stopIndex, rng);
        }

        int n = (int) policyProbsRow.size(1);

        // Copy probs to array and zero out illegal actions.
        // During training we apply temperature scaling:
        // temp > 1.0 -> more exploratory, temp < 1.0 -> more exploitative.
        double[] p = new double[n];
        double sum = 0.0;
        double invTemp = 1.0 / Math.max(temperature, 1e-6);
        for (int a = 0; a < n; a++) {
            double pa = policyProbsRow.getDouble(0, a);
            if (!mask[a]) {
                pa = 0.0;
            } else if (!greedy) {
                pa = Math.pow(Math.max(pa, 1e-12), invTemp);
            }
            p[a] = pa;
            sum += pa;
        }

        // If everything is masked (should not happen if STOP always legal), fallback to STOP
        if (sum <= 0.0) {
            return stopIndex;
        }

        // Renormalize
        for (int a = 0; a < n; a++) p[a] /= sum;

        if (greedy) {
            int best = 0;
            double bestVal = p[0];
            for (int a = 1; a < n; a++) {
                if (p[a] > bestVal) {
                    bestVal = p[a];
                    best = a;
                }
            }
            return best;
        }

        // Sample categorical
        double r = rng.nextDouble();
        double cdf = 0.0;
        for (int a = 0; a < n; a++) {
            cdf += p[a];
            if (r <= cdf) return a;
        }
        return n - 1;
    }

    private static double getExplorationTemperature(int episode) {
        if (episode <= TEMP_WARMUP_EPISODES) {
            return START_TEMPERATURE;
        }
        int decayEpisode = episode - TEMP_WARMUP_EPISODES;
        if (decayEpisode >= TEMP_DECAY_EPISODES) {
            return END_TEMPERATURE;
        }
        double frac = (double) decayEpisode / TEMP_DECAY_EPISODES;
        return START_TEMPERATURE + frac * (END_TEMPERATURE - START_TEMPERATURE);
    }

    private static double guidanceProbAtEpisode(int episode) {
        if (episode >= HEURISTIC_GUIDANCE_DECAY_EPISODES) {
            return HEURISTIC_GUIDANCE_END;
        }
        double frac = (double) episode / HEURISTIC_GUIDANCE_DECAY_EPISODES;
        return HEURISTIC_GUIDANCE_START + frac * (HEURISTIC_GUIDANCE_END - HEURISTIC_GUIDANCE_START);
    }

    private static void trainOnTrajectory(ActorCriticNetwork net, List<StepData> traj) {
        int T = traj.size();
        if (T == 0) return;

        // Compute returns G_t backwards
        double[] returns = new double[T];
        double G = 0.0;
        for (int t = T - 1; t >= 0; t--) {
            StepData sd = traj.get(t);
            if (sd.done) {
                G = sd.reward;
            } else {
                G = sd.reward + GAMMA * G;
            }
            returns[t] = G;
        }

        // Advantages = return - valuePred
        double[] adv = new double[T];
        double mean = 0.0;
        for (int t = 0; t < T; t++) {
            adv[t] = returns[t] - traj.get(t).valuePred;
            mean += adv[t];
        }
        mean /= T;

        double var = 0.0;
        for (int t = 0; t < T; t++) {
            double d = adv[t] - mean;
            var += d * d;
        }
        var /= T;
        double std = Math.sqrt(var) + 1e-8;

        // Normalize advantages (helps stability)
        for (int t = 0; t < T; t++) {
            adv[t] = (adv[t] - mean) / std;
        }

        // Build batches
        int obsSize = traj.get(0).obs.length;
        int actionSize = net.getActionSize();

        INDArray obsBatch = Nd4j.create(T, obsSize);
        INDArray policyLabelBatch = Nd4j.zeros(T, actionSize);
        INDArray valueLabelBatch = Nd4j.create(T, 1);

        for (int i = 0; i < T; i++) {
            StepData sd = traj.get(i);

            // obs
            for (int j = 0; j < obsSize; j++) {
                obsBatch.putScalar(i, j, sd.obs[j]);
            }

            // policy labels: advantage-weighted one-hot
            policyLabelBatch.putScalar(i, sd.action, adv[i]);

            // value labels: returns
            valueLabelBatch.putScalar(i, 0, returns[i]);
        }

        // One gradient step on this episode
        net.fit(obsBatch, policyLabelBatch, valueLabelBatch);
    }

    private static void trainOnTrajectoryBatch(ActorCriticNetwork net, List<List<StepData>> trajectories) {
        for (List<StepData> traj : trajectories) {
            trainOnTrajectory(net, traj);
        }
    }
}
