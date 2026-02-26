package Stochastic.reinforcement_learning.actor_critic_agent;

import org.nd4j.common.primitives.Pair;
import org.nd4j.linalg.activations.IActivation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.lossfunctions.ILossFunction;
import org.nd4j.linalg.ops.transforms.Transforms;

/**
 * Policy gradient loss for discrete actions using softmax output.
 *
 * Labels are advantage-weighted one-hot vectors:
 *   y[a] = advantage, y[others] = 0
 *
 * Loss per example:
 *   L = - sum_j y[j] * log(p[j])
 *     = - advantage * log(p[action])
 *
 * This works for positive or negative advantages.
 */
public final class AdvantageWeightedSoftmaxLoss implements ILossFunction {

    private static final double EPS = 1e-8;

    @Override
    public double computeScore(INDArray labels, INDArray preOutput, IActivation activationFn,
                               INDArray mask, boolean average) {
        INDArray scoreArr = computeScoreArray(labels, preOutput, activationFn, mask);
        double score = scoreArr.sumNumber().doubleValue();
        if (average) {
            score /= scoreArr.size(0);
        }
        return score;
    }

    @Override
    public INDArray computeScoreArray(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask) {
        // p = softmax(z)
        INDArray p = activationFn.getActivation(preOutput.dup(), true);

        // L = -sum(y * log(p))
        INDArray logp = Transforms.log(p.add(EPS), false);
        INDArray perExample = labels.mul(logp).sum(1).negi(); // shape [batch,1] or [batch]

        if (mask != null) {
            // If you later use masks, apply here (rare for non-RNN).
            perExample.muli(mask);
        }
        return perExample;
    }

    @Override
    public INDArray computeGradient(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask) {
        // For softmax cross entropy with non-normalized labels:
        // grad = p * sum(labels) - labels
        INDArray p = activationFn.getActivation(preOutput.dup(), true);

        INDArray sumY = labels.sum(1); // [batch,1] (or [batch])
        if (sumY.rank() == 1) {
            sumY = sumY.reshape(sumY.length(), 1);
        }

        INDArray gradAtOutput = p.mulColumnVector(sumY).sub(labels); // [batch, nOut]

        if (mask != null) {
            gradAtOutput.muliColumnVector(mask);
        }

        // Backprop through activation to get dL/dz
        Pair<INDArray, INDArray> pair = activationFn.backprop(preOutput, gradAtOutput);
        INDArray gradPreOut = pair.getFirst();

        return gradPreOut;
    }

    @Override
    public Pair<Double, INDArray> computeGradientAndScore(INDArray labels, INDArray preOutput, IActivation activationFn,
                                                          INDArray mask, boolean average) {
        double score = computeScore(labels, preOutput, activationFn, mask, average);
        INDArray gradient = computeGradient(labels, preOutput, activationFn, mask);
        return new Pair<>(score, gradient);
    }

    @Override
    public String name() {
        return "AdvantageWeightedSoftmaxLoss";
    }
}