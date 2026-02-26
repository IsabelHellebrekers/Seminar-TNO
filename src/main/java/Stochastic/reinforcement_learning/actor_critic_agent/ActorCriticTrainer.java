package Stochastic.reinforcement_learning.actor_critic_agent;

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

public final class ActorCriticTrainer {

    private static final Logger LOG = Logger.getLogger(ActorCriticTrainer.class.getName());

    // Hyperparameters (start simple)
    private static final double GAMMA = 0.99;
    private static final double LR = 1e-4;

    // If true, choose argmax instead of sampling (evaluation mode)
    private static final boolean GREEDY_ACTIONS = false;

    private record StepData(float[] obs,
                            int action,
                            double reward,
                            boolean done,
                            double valuePred,
                            boolean[] mask) {}

    public static void main(String[] args) throws Exception {

        LOG.setLevel(Level.INFO);

        // TODO: change instance
        Instance instance = InstanceCreator.createFDInstance().get(0);

        DemandModel demandModel = new DemandModel();
        ActionSpace actionSpace = new ActionSpace(instance);

        int mscTrucksPerDay = 65;

        int numFsc = instance.getFSCs().size();
        int[] fscTrucksPerDay = new int[numFsc];

        // TODO: fill with your numbers per FSC
        fscTrucksPerDay[0] = 40;
        fscTrucksPerDay[1] = 40;

        // With actor-critic + masking, throwing on illegal moves is reasonable
        boolean throwOnIllegalAction = true;

        Environment env = new Environment(
                instance,
                actionSpace,
                demandModel,
                mscTrucksPerDay,
                fscTrucksPerDay,
                throwOnIllegalAction
        );

        int horizon = env.getHorizon();

        // Determine observation size
        State init = env.reset(123L);
        int obsSize = init.toObservationVector(horizon).length;

        ActorCriticNetwork net = new ActorCriticNetwork(obsSize, actionSpace.size(), LR);

        int episodes = 200_000;
        int maxStepsPerEpisode = 50_000; // safety cap
        int logEvery = 100;

        Random rng = new Random(123);

        for (int ep = 1; ep <= episodes; ep++) {

            env.reset(rng.nextLong());
            List<StepData> traj = new ArrayList<>();

            boolean done = false;
            double episodeReward = 0.0;
            int stepCount = 0;

            while (!done) {

                float[] obs = env.getCurrentObservationVector();
                boolean[] mask = env.getLegalActionMask();

                INDArray obsRow = Nd4j.create(obs).reshape(1, obs.length);
                INDArray[] out = net.forward(obsRow);

                INDArray policyProbs = out[0]; // [1, actionSize]
                double valuePred = out[1].getDouble(0);

                int action = selectActionMasked(policyProbs, mask, actionSpace.getStopIndex(), rng, GREEDY_ACTIONS);

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

            // Train on the collected trajectory
            trainOnTrajectory(net, traj);

            // Logging you asked for: track stockouts and success episodes
            double stockoutKg = env.getLastEpisodeTotalStockoutKg();
            int daysSurvived = env.getLastEpisodeDaysSurvived();

            if (ep % 10 == 0 || stockoutKg == 0.0) {
                LOG.info(String.format(
                        "EP=%d | steps=%d | reward=%.3f | daysSurvived=%d | stockoutKg=%.3f",
                        ep, stepCount, episodeReward, daysSurvived, stockoutKg
                ));
            }
        }
    }

    private static int selectActionMasked(INDArray policyProbsRow,
                                          boolean[] mask,
                                          int stopIndex,
                                          Random rng,
                                          boolean greedy) {

        int n = (int) policyProbsRow.size(1);

        // Copy probs to array and zero out illegal actions
        double[] p = new double[n];
        double sum = 0.0;
        for (int a = 0; a < n; a++) {
            double pa = policyProbsRow.getDouble(0, a);
            if (!mask[a]) {
                pa = 0.0;
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
}