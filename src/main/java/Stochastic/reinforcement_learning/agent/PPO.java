package Stochastic.reinforcement_learning.agent;

import Stochastic.reinforcement_learning.Environment;
import Stochastic.reinforcement_learning.HeuristicGuidancePolicy;
import Stochastic.reinforcement_learning.StepResult;
import Stochastic.reinforcement_learning.State;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;
import java.util.function.Supplier;

public final class PPO {

    private final Environment env;
    private final ActorCritic ac;
    private final RunningNorm norm;

    private final int actDim;
    private final Random rng;
    private final long[] fixedEvalSeeds;
    private final HeuristicGuidancePolicy heuristic;
    private final Supplier<Environment> evalEnvFactory;

    // PPO hyperparams
    private final float gamma = 0.99f;
    private final float lam = 0.95f;
    private final float clipEps = 0.15f;
    private final float valueCoef = 0.5f;
    private final float entropyCoefStart = 0.015f;
    private final float entropyCoefEnd = 0.0005f;
    private final float targetKl = 0.02f;
    private final float rewardClipAbs = 20f;
    private final float rewardScale = 10f;
    private final float heuristicBcCoefStart = 0.20f;
    private final float heuristicBcCoefEnd = 0.01f;

    public PPO(Environment env, ActorCritic ac, RunningNorm norm, int actDim, Random rng) {
        this(env, ac, norm, actDim, rng, null, null);
    }

    public PPO(Environment env, ActorCritic ac, RunningNorm norm, int actDim, Random rng, HeuristicGuidancePolicy heuristic) {
        this(env, ac, norm, actDim, rng, heuristic, null);
    }

    public PPO(Environment env, ActorCritic ac, RunningNorm norm, int actDim, Random rng,
               HeuristicGuidancePolicy heuristic, Supplier<Environment> evalEnvFactory) {
        this.env = env;
        this.ac = ac;
        this.norm = norm;
        this.actDim = actDim;
        this.rng = rng;
        this.heuristic = heuristic;
        this.evalEnvFactory = evalEnvFactory;

        this.fixedEvalSeeds = new long[4096];
        Random evalSeedRng = new Random(20260303L);
        for (int i = 0; i < fixedEvalSeeds.length; i++) {
            fixedEvalSeeds[i] = evalSeedRng.nextLong();
        }
    }

    public void train(int iterations, int rolloutSteps, int updateEpochs) {
        train(iterations, rolloutSteps, updateEpochs, 300, 20);
    }

    public void train(int iterations, int rolloutSteps, int updateEpochs, int evalEpisodes, int evalEvery) {
        long seed0 = 1234L;
        double bestDaysMean = -Double.MAX_VALUE;

        for (int it = 1; it <= iterations; it++) {
            float entropyCoef = entropyCoefAt(it, iterations);
            float heuristicBcCoef = heuristicBcCoefAt(it, iterations);
            Rollout ro = collectRollout(rolloutSteps, seed0 + it);
            TrainRolloutStats rolloutStats = summarizeRollout(ro, env.getActionSpace().getStopIndex());

            int n = ro.size();
            int[] order = new int[n];
            for (int i = 0; i < n; i++) {
                order[i] = i;
            }

            double sumApproxKl = 0.0;
            double sumEntropy = 0.0;
            double sumValueMse = 0.0;
            int clippedCount = 0;
            int updateCount = 0;
            int epochsRun = 0;
            int earlyStopEpoch = 0;

            for (int ep = 0; ep < updateEpochs; ep++) {
                shuffleInPlace(order, rng);
                double epochApproxKlSum = 0.0;
                int epochUpdates = 0;
                for (int pos = 0; pos < n; pos++) {
                    Rollout.Step s = ro.steps().get(order[pos]);

                    float[] logits = ac.policyLogits(s.obs);
                    MaskedCategorical dist = new MaskedCategorical(logits, s.mask);

                    float newLogp = dist.logProb(s.action);
                    float ratio = (float) Math.exp(newLogp - s.oldLogp);
                    float adv = s.advantage;
                    updateCount++;
                    float approxKl = (s.oldLogp - newLogp);
                    sumApproxKl += approxKl;
                    epochApproxKlSum += approxKl;
                    epochUpdates++;
                    if (ratio > 1f + clipEps || ratio < 1f - clipEps) {
                        clippedCount++;
                    }

                    float dLossDLogp;
                    if ((adv > 0f && ratio > 1f + clipEps) || (adv < 0f && ratio < 1f - clipEps)) {
                        dLossDLogp = 0f;
                    } else {
                        dLossDLogp = -adv * ratio;
                    }

                    float[] pi = dist.probs();
                    sumEntropy += categoricalEntropy(pi);
                    float[] dLogits = new float[actDim];
                    for (int i = 0; i < actDim; i++) {
                        dLogits[i] = dLossDLogp * (-pi[i]);
                    }
                    dLogits[s.action] += dLossDLogp;
                    addEntropyGradient(dLogits, pi, entropyCoef);
                    addHeuristicBcGradient(dLogits, pi, s.heuristicAction, heuristicBcCoef);

                    float v = ac.value(s.obs);
                    double ve = v - s.ret;
                    sumValueMse += ve * ve;
                    float dV = valueCoef * (v - s.ret);
                    ac.backwardAndStep(s.obs, dLogits, dV);
                }
                epochsRun++;
                double epochApproxKl = epochUpdates == 0 ? 0.0 : epochApproxKlSum / epochUpdates;
                if (epochApproxKl > targetKl) {
                    earlyStopEpoch = ep + 1;
                    break;
                }
            }

            TrainUpdateStats updateStats = new TrainUpdateStats(
                    updateCount == 0 ? 0.0 : sumApproxKl / updateCount,
                    updateCount == 0 ? 0.0 : ((double) clippedCount / updateCount),
                    updateCount == 0 ? 0.0 : sumEntropy / updateCount,
                    updateCount == 0 ? 0.0 : sumValueMse / updateCount,
                    epochsRun,
                    earlyStopEpoch
            );

            if (it % evalEvery == 0) {
                EvalReport report = evaluate(evalEpisodes);
                boolean improved = report.daysSurvived.mean > bestDaysMean + 1e-9;
                if (improved) {
                    bestDaysMean = report.daysSurvived.mean;
                }
                System.out.println(
                        "iter=" + it +
                        " rolloutLen=" + ro.size() +
                        " entropyCoef=" + String.format(Locale.ROOT, "%.4f", entropyCoef) +
                        " heuristicBcCoef=" + String.format(Locale.ROOT, "%.4f", heuristicBcCoef) +
                        " bestDaysMean=" + String.format(Locale.ROOT, "%.3f", bestDaysMean) +
                        " improved=" + improved +
                        " " + rolloutStats.toSingleLine() +
                        " " + updateStats.toSingleLine() +
                        " " + report.toSingleLine()
                );
            }
        }
    }

    public void trainOvernight(long hours,
                               int rolloutSteps,
                               int updateEpochs,
                               int evalEpisodes,
                               int evalEveryIterations,
                               int evalThreads,
                               Path runDir) {
        long budgetNanos = hours * 3600L * 1_000_000_000L;
        long start = System.nanoTime();
        long nextCheckpointNanos = 3600L * 1_000_000_000L;

        double bestDaysMean = -Double.MAX_VALUE;
        int it = 0;
        int evalCount = 0;

        Path metricsCsv = runDir.resolve("overnight_metrics.csv");
        Path checkpointsDir = runDir.resolve("checkpoints");
        ensureMetricsHeader(metricsCsv);

        while (System.nanoTime() - start < budgetNanos) {
            it++;
            float progress = Math.min(1f, (float) (System.nanoTime() - start) / (float) budgetNanos);
            float entropyCoef = entropyCoefAtProgress(progress);
            float heuristicBcCoef = heuristicBcCoefAtProgress(progress);

            Rollout ro = collectRollout(rolloutSteps, 1234L + it);
            TrainRolloutStats rolloutStats = summarizeRollout(ro, env.getActionSpace().getStopIndex());
            TrainUpdateStats updateStats = updateFromRollout(ro, updateEpochs, entropyCoef, heuristicBcCoef);

            if (it % evalEveryIterations == 0) {
                evalCount++;
                EvalReport report = evaluate(evalEpisodes, evalThreads);
                boolean improved = report.daysSurvived.mean > bestDaysMean + 1e-9;
                if (improved) {
                    bestDaysMean = report.daysSurvived.mean;
                    saveCheckpoint(checkpointsDir.resolve("best_days_iter_" + it + ".bin"));
                }

                long elapsedNanos = System.nanoTime() - start;
                double elapsedHours = elapsedNanos / 3_600_000_000_000.0;
                double progressPct = Math.min(100.0, 100.0 * elapsedNanos / budgetNanos);

                appendMetrics(metricsCsv, it, elapsedHours, progressPct, entropyCoef, heuristicBcCoef, rolloutStats, updateStats, report);
                System.out.println(
                        "iter=" + it +
                        " elapsedH=" + String.format(Locale.ROOT, "%.2f", elapsedHours) +
                        " progressPct=" + String.format(Locale.ROOT, "%.1f", progressPct) +
                        " entropyCoef=" + String.format(Locale.ROOT, "%.4f", entropyCoef) +
                        " heuristicBcCoef=" + String.format(Locale.ROOT, "%.4f", heuristicBcCoef) +
                        " bestDaysMean=" + String.format(Locale.ROOT, "%.3f", bestDaysMean) +
                        " improved=" + improved +
                        " " + rolloutStats.toSingleLine() +
                        " " + updateStats.toSingleLine() +
                        " " + report.toSingleLine()
                );
            }

            long nowElapsed = System.nanoTime() - start;
            if (nowElapsed >= nextCheckpointNanos) {
                saveCheckpoint(checkpointsDir.resolve("hour_" + (nextCheckpointNanos / 3_600_000_000_000L) + "_iter_" + it + ".bin"));
                nextCheckpointNanos += 3600L * 1_000_000_000L;
            }
        }
    }

    private TrainUpdateStats updateFromRollout(Rollout ro, int updateEpochs, float entropyCoef, float heuristicBcCoef) {
        int n = ro.size();
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = i;

        double sumApproxKl = 0.0;
        double sumEntropy = 0.0;
        double sumValueMse = 0.0;
        int clippedCount = 0;
        int updateCount = 0;
        int epochsRun = 0;
        int earlyStopEpoch = 0;

        for (int ep = 0; ep < updateEpochs; ep++) {
            shuffleInPlace(order, rng);
            double epochApproxKlSum = 0.0;
            int epochUpdates = 0;
            for (int pos = 0; pos < n; pos++) {
                Rollout.Step s = ro.steps().get(order[pos]);

                float[] logits = ac.policyLogits(s.obs);
                MaskedCategorical dist = new MaskedCategorical(logits, s.mask);

                float newLogp = dist.logProb(s.action);
                float ratio = (float) Math.exp(newLogp - s.oldLogp);
                float adv = s.advantage;
                updateCount++;
                float approxKl = (s.oldLogp - newLogp);
                sumApproxKl += approxKl;
                epochApproxKlSum += approxKl;
                epochUpdates++;
                if (ratio > 1f + clipEps || ratio < 1f - clipEps) {
                    clippedCount++;
                }

                float dLossDLogp;
                if ((adv > 0f && ratio > 1f + clipEps) || (adv < 0f && ratio < 1f - clipEps)) {
                    dLossDLogp = 0f;
                } else {
                    dLossDLogp = -adv * ratio;
                }

                float[] pi = dist.probs();
                sumEntropy += categoricalEntropy(pi);
                float[] dLogits = new float[actDim];
                for (int i = 0; i < actDim; i++) dLogits[i] = dLossDLogp * (-pi[i]);
                dLogits[s.action] += dLossDLogp;
                addEntropyGradient(dLogits, pi, entropyCoef);
                addHeuristicBcGradient(dLogits, pi, s.heuristicAction, heuristicBcCoef);

                float v = ac.value(s.obs);
                double ve = v - s.ret;
                sumValueMse += ve * ve;
                float dV = valueCoef * (v - s.ret);
                ac.backwardAndStep(s.obs, dLogits, dV);
            }
            epochsRun++;
            double epochApproxKl = epochUpdates == 0 ? 0.0 : epochApproxKlSum / epochUpdates;
            if (epochApproxKl > targetKl) {
                earlyStopEpoch = ep + 1;
                break;
            }
        }

        return new TrainUpdateStats(
                updateCount == 0 ? 0.0 : sumApproxKl / updateCount,
                updateCount == 0 ? 0.0 : ((double) clippedCount / updateCount),
                updateCount == 0 ? 0.0 : sumEntropy / updateCount,
                updateCount == 0 ? 0.0 : sumValueMse / updateCount,
                epochsRun,
                earlyStopEpoch
        );
    }

    private Rollout collectRollout(int stepsTarget, long seed) {
        env.reset(seed);
        Rollout ro = new Rollout();

        int steps = 0;
        while (steps < stepsTarget) {
            float[] raw = env.getCurrentObservationVector();
            norm.update(raw);
            float[] obs = norm.normalize(raw);

            boolean[] mask = env.getLegalActionMask();
            int heuristicAction = -1;
            if (heuristic != null) {
                State st = env.getCurrentStateCopy();
                heuristicAction = heuristic.selectAction(st, mask, env.getActionSpace().getStopIndex(), rng);
            }
            float v = ac.value(obs);
            float[] logits = ac.policyLogits(obs);
            MaskedCategorical dist = new MaskedCategorical(logits, mask);

            int a = dist.sample(rng);
            float logp = dist.logProb(a);

            StepResult sr = env.step(a);
            float trainReward = normalizeRewardForTraining((float) sr.reward());
            ro.add(obs, mask, a, heuristicAction, logp, v, trainReward, sr.done());

            steps++;
            if (sr.done()) {
                env.reset(rng.nextLong());
            }
        }

        float[] rawLast = env.getCurrentObservationVector();
        float[] obsLast = norm.normalize(rawLast);
        float lastV = ac.value(obsLast);
        ro.computeGAE(lastV, gamma, lam);
        return ro;
    }

    private EvalReport evaluate(int episodes) {
        return evaluate(episodes, 1);
    }

    private EvalReport evaluate(int episodes, int evalThreads) {
        if (episodes <= 0) {
            throw new IllegalArgumentException("episodes must be > 0");
        }

        double[] returns = new double[episodes];
        double[] daysSurvived = new double[episodes];
        double[] stockoutKg = new double[episodes];
        int[] stockoutDayHist = new int[Math.max(1, env.getHorizon() + 1)];

        if (evalThreads <= 1 || evalEnvFactory == null) {
            runSequentialEval(episodes, returns, daysSurvived, stockoutKg, stockoutDayHist);
        } else {
            runParallelEval(episodes, evalThreads, returns, daysSurvived, stockoutKg, stockoutDayHist);
        }

        double successes = 0.0;
        double dayGe5Count = 0.0;
        double dayGe6Count = 0.0;
        for (int ep = 0; ep < episodes; ep++) {
            int day = (int) daysSurvived[ep];
            if (day >= 5) dayGe5Count += 1.0;
            if (day >= 6) dayGe6Count += 1.0;
            if (day >= env.getHorizon()) successes += 1.0;
        }

        return new EvalReport(
                summarize("return", returns),
                summarize("daysSurvived", daysSurvived),
                summarize("stockoutKg", stockoutKg),
                successes / episodes,
                dayGe5Count / episodes,
                dayGe6Count / episodes,
                episodes,
                stockoutDayHist
        );
    }

    private void runSequentialEval(int episodes, double[] returns, double[] daysSurvived, double[] stockoutKg, int[] stockoutDayHist) {
        for (int ep = 0; ep < episodes; ep++) {
            long seed = fixedEvalSeeds[ep % fixedEvalSeeds.length];
            env.reset(seed);
            double ret = 0.0;

            while (true) {
                float[] obs = norm.normalize(env.getCurrentObservationVector());
                boolean[] mask = env.getLegalActionMask();

                float[] logits = ac.policyLogits(obs);
                int best = -1;
                float bestVal = -Float.MAX_VALUE;
                for (int i = 0; i < actDim; i++) {
                    if (!mask[i]) continue;
                    if (logits[i] > bestVal) { bestVal = logits[i]; best = i; }
                }

                StepResult sr = env.step(best);
                ret += sr.reward();
                if (sr.done()) break;
            }

            returns[ep] = ret;
            daysSurvived[ep] = env.getLastEpisodeDaysSurvived();
            stockoutKg[ep] = env.getLastEpisodeTotalStockoutKg();
            int day = env.getLastEpisodeDaysSurvived();
            if (day < env.getHorizon()) {
                int idx = Math.max(0, Math.min(stockoutDayHist.length - 1, day));
                stockoutDayHist[idx]++;
            }
        }
    }

    private void runParallelEval(int episodes, int evalThreads, double[] returns, double[] daysSurvived, double[] stockoutKg, int[] stockoutDayHist) {
        ForkJoinPool pool = new ForkJoinPool(evalThreads);
        try {
            pool.submit(() -> IntStream.range(0, episodes).parallel().forEach(ep -> {
                Environment localEnv = evalEnvFactory.get();
                localEnv.setEpisodeEventLoggingEnabled(false);
                long seed = fixedEvalSeeds[ep % fixedEvalSeeds.length];
                localEnv.reset(seed);
                double ret = 0.0;

                while (true) {
                    float[] obs = norm.normalize(localEnv.getCurrentObservationVector());
                    boolean[] mask = localEnv.getLegalActionMask();

                    float[] logits = ac.policyLogits(obs);
                    int best = -1;
                    float bestVal = -Float.MAX_VALUE;
                    for (int i = 0; i < actDim; i++) {
                        if (!mask[i]) continue;
                        if (logits[i] > bestVal) { bestVal = logits[i]; best = i; }
                    }

                    StepResult sr = localEnv.step(best);
                    ret += sr.reward();
                    if (sr.done()) break;
                }

                returns[ep] = ret;
                daysSurvived[ep] = localEnv.getLastEpisodeDaysSurvived();
                stockoutKg[ep] = localEnv.getLastEpisodeTotalStockoutKg();
            })).join();
        } finally {
            pool.shutdown();
        }

        for (int ep = 0; ep < episodes; ep++) {
            int day = (int) daysSurvived[ep];
            if (day < env.getHorizon()) {
                int idx = Math.max(0, Math.min(stockoutDayHist.length - 1, day));
                stockoutDayHist[idx]++;
            }
        }
    }

    private static float entropyCoefAt(int it, int totalIters) {
        if (totalIters <= 1) {
            return 0.0005f;
        }
        float t = (float) (it - 1) / (float) (totalIters - 1);
        // Quadratic decay keeps exploration early and pushes determinism late.
        float decay = (1f - t) * (1f - t);
        return 0.0005f + decay * (0.015f - 0.0005f);
    }

    private float entropyCoefAtProgress(float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        float decay = (1f - p) * (1f - p);
        return entropyCoefEnd + decay * (entropyCoefStart - entropyCoefEnd);
    }

    private float heuristicBcCoefAt(int it, int totalIters) {
        if (heuristic == null) {
            return 0f;
        }
        if (totalIters <= 1) {
            return heuristicBcCoefEnd;
        }
        float t = (float) (it - 1) / (float) (totalIters - 1);
        // Decay teacher forcing over training so RL can surpass heuristic later.
        return heuristicBcCoefStart + t * (heuristicBcCoefEnd - heuristicBcCoefStart);
    }

    private float heuristicBcCoefAtProgress(float progress) {
        if (heuristic == null) return 0f;
        float p = Math.max(0f, Math.min(1f, progress));
        return heuristicBcCoefStart + p * (heuristicBcCoefEnd - heuristicBcCoefStart);
    }

    private static void shuffleInPlace(int[] arr, Random rng) {
        for (int i = arr.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = arr[i];
            arr[i] = arr[j];
            arr[j] = tmp;
        }
    }

    private static void addEntropyGradient(float[] dLogits, float[] probs, float entropyCoef) {
        if (entropyCoef <= 0f) {
            return;
        }

        double s = 0.0;
        for (float p : probs) {
            if (p > 0f) {
                s += p * (Math.log(p) + 1.0);
            }
        }

        for (int i = 0; i < dLogits.length; i++) {
            float p = probs[i];
            if (p <= 0f) {
                continue;
            }
            float li = (float) Math.log(p);
            float dHdz = p * ((float) s - (li + 1f));
            dLogits[i] += -entropyCoef * dHdz;
        }
    }

    private static void addHeuristicBcGradient(float[] dLogits, float[] probs, int heuristicAction, float bcCoef) {
        if (bcCoef <= 0f || heuristicAction < 0 || heuristicAction >= dLogits.length) {
            return;
        }
        // Cross-entropy gradient: pi - onehot(heuristicAction)
        for (int i = 0; i < dLogits.length; i++) {
            dLogits[i] += bcCoef * probs[i];
        }
        dLogits[heuristicAction] -= bcCoef;
    }

    private void ensureMetricsHeader(Path csvPath) {
        try {
            Path parent = csvPath.getParent();
            if (parent != null) Files.createDirectories(parent);
            if (!Files.exists(csvPath)) {
                String header = "iter,elapsed_hours,progress_pct,entropy_coef,heuristic_bc_coef,best_days_mean,succ,day_ge5,day_ge6,days_mean,days_p50,days_p90,return_mean,stockout_mean,approx_kl,clip_frac,value_mse,heur_match\n";
                Files.writeString(csvPath, header, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize metrics CSV", e);
        }
    }

    private void appendMetrics(Path csvPath, int iter, double elapsedHours, double progressPct,
                               float entropyCoef, float heuristicBcCoef,
                               TrainRolloutStats rolloutStats, TrainUpdateStats updateStats, EvalReport report) {
        String row = String.format(Locale.ROOT,
                "%d,%.4f,%.2f,%.6f,%.6f,%.4f,%.6f,%.6f,%.6f,%.4f,%.4f,%.4f,%.4f,%.4f,%.6f,%.6f,%.6f,%.6f%n",
                iter, elapsedHours, progressPct, entropyCoef, heuristicBcCoef, report.daysSurvived.mean,
                report.successRate, report.dayGe5Rate, report.dayGe6Rate,
                report.daysSurvived.mean, report.daysSurvived.p50, report.daysSurvived.p90,
                report.returns.mean, report.stockoutKg.mean,
                updateStats.approxKl, updateStats.clipFrac, updateStats.valueMse, rolloutStats.heuristicMatchFrac);
        try {
            Files.writeString(csvPath, row, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException("Failed writing metrics CSV", e);
        }
    }

    private void saveCheckpoint(Path path) {
        try {
            ac.saveSnapshot(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed saving checkpoint to " + path, e);
        }
    }

    private float normalizeRewardForTraining(float reward) {
        float clipped = Math.max(-rewardClipAbs, Math.min(rewardClipAbs, reward));
        return clipped / rewardScale;
    }

    private static double categoricalEntropy(float[] probs) {
        double h = 0.0;
        for (float p : probs) {
            if (p > 0f) {
                h += -p * Math.log(p);
            }
        }
        return h;
    }

    private static TrainRolloutStats summarizeRollout(Rollout ro, int stopIndex) {
        int total = ro.size();
        int episodes = 0;
        int stopActions = 0;
        int totalLegal = 0;
        int totalMaskCounted = 0;
        double sumAdvAbs = 0.0;
        double epReturn = 0.0;
        double sumEpReturn = 0.0;
        int epLen = 0;
        int sumEpLen = 0;

        for (Rollout.Step s : ro.steps()) {
            epReturn += s.reward;
            epLen++;
            if (s.mask != null) {
                for (boolean b : s.mask) {
                    if (b) {
                        totalLegal++;
                    }
                }
                totalMaskCounted++;
            }
            if (s.action == stopIndex) {
                stopActions++;
            }
            sumAdvAbs += Math.abs(s.advantage);
            if (s.done) {
                episodes++;
                sumEpReturn += epReturn;
                sumEpLen += epLen;
                epReturn = 0.0;
                epLen = 0;
            }
        }

        double meanEpRet = episodes == 0 ? 0.0 : sumEpReturn / episodes;
        double meanEpLen = episodes == 0 ? 0.0 : ((double) sumEpLen / episodes);
        double meanLegal = totalMaskCounted == 0 ? 0.0 : ((double) totalLegal / totalMaskCounted);
        double stopFrac = total == 0 ? 0.0 : ((double) stopActions / total);
        double meanAbsAdv = total == 0 ? 0.0 : (sumAdvAbs / total);
        int heuristicComparable = 0;
        int heuristicMatches = 0;
        for (Rollout.Step s : ro.steps()) {
            if (s.heuristicAction >= 0) {
                heuristicComparable++;
                if (s.action == s.heuristicAction) {
                    heuristicMatches++;
                }
            }
        }
        double heuristicMatchFrac = heuristicComparable == 0 ? 0.0 : ((double) heuristicMatches / heuristicComparable);

        return new TrainRolloutStats(total, episodes, meanEpRet, meanEpLen, meanLegal, stopFrac, meanAbsAdv, heuristicMatchFrac);
    }

    private static String formatSparseHistogram(int[] counts) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] <= 0) {
                continue;
            }
            if (!first) {
                sb.append(",");
            }
            sb.append(i).append(":").append(counts[i]);
            first = false;
        }
        if (first) {
            sb.append("none");
        }
        sb.append("}");
        return sb.toString();
    }

    private static MetricSummary summarize(String name, double[] values) {
        int n = values.length;
        if (n == 0) {
            return new MetricSummary(name, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0);
        }

        double mean = 0.0;
        for (double v : values) {
            mean += v;
        }
        mean /= n;

        double var = 0.0;
        for (double v : values) {
            double d = v - mean;
            var += d * d;
        }
        double std = Math.sqrt(var / Math.max(1, n - 1));
        double se = std / Math.sqrt(n);
        double ci95HalfWidth = 1.96 * se;

        double[] sorted = Arrays.copyOf(values, n);
        Arrays.sort(sorted);
        double min = sorted[0];
        double p50 = percentileSorted(sorted, 0.50);
        double p90 = percentileSorted(sorted, 0.90);
        double max = sorted[n - 1];

        return new MetricSummary(
                name, mean, std, mean - ci95HalfWidth, mean + ci95HalfWidth, min, p50, p90, max, n
        );
    }

    private static double percentileSorted(double[] sorted, double q) {
        if (sorted.length == 0) {
            return 0.0;
        }
        double pos = q * (sorted.length - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        if (lo == hi) {
            return sorted[lo];
        }
        double w = pos - lo;
        return sorted[lo] * (1.0 - w) + sorted[hi] * w;
    }

    public static final class EvalReport {
        public final MetricSummary returns;
        public final MetricSummary daysSurvived;
        public final MetricSummary stockoutKg;
        public final double successRate;
        public final double dayGe5Rate;
        public final double dayGe6Rate;
        public final int episodes;
        public final int[] stockoutDayHist;

        public EvalReport(MetricSummary returns,
                          MetricSummary daysSurvived,
                          MetricSummary stockoutKg,
                          double successRate,
                          double dayGe5Rate,
                          double dayGe6Rate,
                          int episodes,
                          int[] stockoutDayHist) {
            this.returns = returns;
            this.daysSurvived = daysSurvived;
            this.stockoutKg = stockoutKg;
            this.successRate = successRate;
            this.dayGe5Rate = dayGe5Rate;
            this.dayGe6Rate = dayGe6Rate;
            this.episodes = episodes;
            this.stockoutDayHist = stockoutDayHist;
        }

        public String toSingleLine() {
            return String.format(
                    Locale.ROOT,
                    "eval{n=%d succ=%.4f dayGe5=%.4f dayGe6=%.4f return=%.4f+/-%.4f ci95=[%.4f,%.4f] p50=%.4f p90=%.4f days=%.3f+/-%.3f ci95=[%.3f,%.3f] p50=%.1f p90=%.1f min=%.1f max=%.1f stockoutKg=%.1f+/-%.1f ci95=[%.1f,%.1f] p50=%.1f p90=%.1f stockoutDayHist=%s}",
                    episodes,
                    successRate,
                    dayGe5Rate,
                    dayGe6Rate,
                    returns.mean, returns.std, returns.ci95Low, returns.ci95High, returns.p50, returns.p90,
                    daysSurvived.mean, daysSurvived.std, daysSurvived.ci95Low, daysSurvived.ci95High,
                    daysSurvived.p50, daysSurvived.p90, daysSurvived.min, daysSurvived.max,
                    stockoutKg.mean, stockoutKg.std, stockoutKg.ci95Low, stockoutKg.ci95High, stockoutKg.p50, stockoutKg.p90,
                    formatSparseHistogram(stockoutDayHist)
            );
        }
    }

    public static final class TrainRolloutStats {
        public final int steps;
        public final int episodes;
        public final double meanEpisodeReturn;
        public final double meanEpisodeLen;
        public final double meanLegalActions;
        public final double stopActionFrac;
        public final double meanAbsAdvantage;
        public final double heuristicMatchFrac;

        public TrainRolloutStats(int steps, int episodes, double meanEpisodeReturn, double meanEpisodeLen,
                                 double meanLegalActions, double stopActionFrac, double meanAbsAdvantage,
                                 double heuristicMatchFrac) {
            this.steps = steps;
            this.episodes = episodes;
            this.meanEpisodeReturn = meanEpisodeReturn;
            this.meanEpisodeLen = meanEpisodeLen;
            this.meanLegalActions = meanLegalActions;
            this.stopActionFrac = stopActionFrac;
            this.meanAbsAdvantage = meanAbsAdvantage;
            this.heuristicMatchFrac = heuristicMatchFrac;
        }

        public String toSingleLine() {
            return String.format(
                    Locale.ROOT,
                    "trainRollout{steps=%d eps=%d epRet=%.3f epLen=%.2f legalActs=%.1f stopFrac=%.3f absAdv=%.3f heurMatch=%.3f}",
                    steps, episodes, meanEpisodeReturn, meanEpisodeLen, meanLegalActions, stopActionFrac, meanAbsAdvantage, heuristicMatchFrac
            );
        }
    }

    public static final class TrainUpdateStats {
        public final double approxKl;
        public final double clipFrac;
        public final double policyEntropy;
        public final double valueMse;
        public final int epochsRun;
        public final int earlyStopEpoch;

        public TrainUpdateStats(double approxKl, double clipFrac, double policyEntropy, double valueMse,
                                int epochsRun, int earlyStopEpoch) {
            this.approxKl = approxKl;
            this.clipFrac = clipFrac;
            this.policyEntropy = policyEntropy;
            this.valueMse = valueMse;
            this.epochsRun = epochsRun;
            this.earlyStopEpoch = earlyStopEpoch;
        }

        public String toSingleLine() {
            return String.format(
                    Locale.ROOT,
                    "trainUpdate{approxKL=%.5f clipFrac=%.3f entropy=%.3f valueMSE=%.3f epochsRun=%d earlyStopEpoch=%d}",
                    approxKl, clipFrac, policyEntropy, valueMse, epochsRun, earlyStopEpoch
            );
        }
    }

    public static final class MetricSummary {
        public final String name;
        public final double mean;
        public final double std;
        public final double ci95Low;
        public final double ci95High;
        public final double min;
        public final double p50;
        public final double p90;
        public final double max;
        public final int n;

        public MetricSummary(String name, double mean, double std, double ci95Low, double ci95High,
                             double min, double p50, double p90, double max, int n) {
            this.name = name;
            this.mean = mean;
            this.std = std;
            this.ci95Low = ci95Low;
            this.ci95High = ci95High;
            this.min = min;
            this.p50 = p50;
            this.p90 = p90;
            this.max = max;
            this.n = n;
        }
    }
}
