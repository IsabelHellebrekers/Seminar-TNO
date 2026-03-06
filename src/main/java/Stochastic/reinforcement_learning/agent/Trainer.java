package Stochastic.reinforcement_learning.agent;

import java.util.Random;
import java.util.function.Supplier;
import java.nio.file.Path;

import DataUtils.InstanceCreator;
import Objects.*;
import Stochastic.reinforcement_learning.*;

public final class Trainer {
    public static void main(String[] args) {
        // TODO: build these from your project
        Instance instance = InstanceCreator.createFDInstance().get(0);
        DemandModel demandModel = new DemandModel();
        ActionSpace actionSpace = new ActionSpace(instance);
        HeuristicGuidancePolicy heuristic = new HeuristicGuidancePolicy(instance, actionSpace);

        int maxMscTrucksPerDay = 60;
        int[] maxFscTrucksPerDay = new int[instance.getFSCs().size()];
        for (int i = 0; i < maxFscTrucksPerDay.length; i++) maxFscTrucksPerDay[i] = 40;
        final boolean forceUseAllTrucksInPhase = false;
        final boolean denseShapingEnabled = true;
        final double denseShapingCoef = 0.02;

        Environment env = new Environment(
                instance,
                actionSpace,
                demandModel,
                maxMscTrucksPerDay,
                maxFscTrucksPerDay,
                forceUseAllTrucksInPhase,
                denseShapingEnabled,
                denseShapingCoef
        );
        env.setEpisodeEventLoggingEnabled(false); // Keep console focused on periodic summaries only.
        Supplier<Environment> evalEnvFactory = () -> {
            Environment e = new Environment(
                    instance,
                    actionSpace,
                    demandModel,
                    maxMscTrucksPerDay,
                    maxFscTrucksPerDay,
                    forceUseAllTrucksInPhase,
                    denseShapingEnabled,
                    denseShapingCoef
            );
            e.setEpisodeEventLoggingEnabled(false);
            return e;
        };

        env.reset(0L);

        int obsDim = env.getCurrentObservationVector().length;
        int actDim = actionSpace.size();

        RunningNorm norm = new RunningNorm(obsDim, 5f);

        Random rng = new Random(0);

        ActorCritic ac = new ActorCritic(
                obsDim, actDim,
                64,        // hidden
                1e-4f,     // actor lr (stabler PPO updates)
                5e-4f,     // critic lr (stabilize value learning)
                rng
        );

        PPO ppo = new PPO(env, ac, norm, actDim, rng, heuristic, evalEnvFactory);

        // Overnight profile: log + checkpoint every few minutes/iterations, and evaluate on 4 threads.
        ppo.trainOvernight(
                14,    // hours budget
                4096,  // rollout steps per iter
                3,     // update epochs (less aggressive)
                300,   // eval episodes (less noisy)
                20,    // evaluate every N iterations
                4,     // evaluation threads (target 4 CPU cores for eval load)
                Path.of("results", "overnight_run")
        );
    }
}
