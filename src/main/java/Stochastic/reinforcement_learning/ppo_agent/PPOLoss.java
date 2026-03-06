package Stochastic.reinforcement_learning.ppo_agent;

import org.nd4j.common.primitives.Pair;
import org.nd4j.linalg.activations.IActivation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.ILossFunction;
import org.nd4j.linalg.ops.transforms.Transforms;

/**
 * PPO clipped policy loss for discrete actions.
 *
 * Network policy output must be LOGITS with Activation.IDENTITY.
 *
 * Labels layout (per row):
 *  - [0..actionSize-1] : one-hot(action)
 *  - [actionSize]      : advantage (A_t)
 *  - [actionSize+1]    : oldLogProb (log pi_old(a_t|s_t))
 *
 * Mask:
 *  - Use the label mask array for this output with shape [batch, actionSize],
 *    where mask[b,a]=1 if action is legal else 0.
 *
 * Loss per sample (we minimize):
 *   L = - min(r*A, clip(r,1-eps,1+eps)*A) - entropyCoef * H(pi)
 * where r = exp(logp - oldLogp).
 */
public final class PPOLoss implements ILossFunction {

    private static final double EPS = 1e-8;

    private final int actionSize;
    private final double clipEps;
    private final double entropyCoef;

    public PPOLoss(int actionSize, double clipEps, double entropyCoef) {
        this.actionSize = actionSize;
        this.clipEps = clipEps;
        this.entropyCoef = entropyCoef;
    }

    @Override
    public double computeScore(INDArray labels, INDArray preOutput, IActivation activationFn,
                               INDArray mask, boolean average) {
        INDArray scoreArr = computeScoreArray(labels, preOutput, activationFn, mask);
        double score = scoreArr.sumNumber().doubleValue();
        if (average) score /= scoreArr.size(0);
        return score;
    }

    @Override
    public INDArray computeScoreArray(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask) {
        // logits = preOutput (since policy head uses Activation.IDENTITY)
        INDArray logits = preOutput.dup();

        // mask: [batch, actionSize] with 1 legal, 0 illegal (may come in as INT/BOOL)
        INDArray legalMask = (mask == null) ? Nd4j.onesLike(logits) : mask.castTo(logits.dataType());

        // masked softmax
        INDArray probs = maskedSoftmax(logits, legalMask);

        // labels
        INDArray oneHot = labels.get(NDArrayIndex.all(), NDArrayIndex.interval(0, actionSize));
        INDArray adv = labels.get(NDArrayIndex.all(), NDArrayIndex.point(actionSize)).reshape(labels.size(0), 1);
        INDArray oldLogP = labels.get(NDArrayIndex.all(), NDArrayIndex.point(actionSize + 1)).reshape(labels.size(0), 1);

        // logp(a)
        INDArray logProbs = Transforms.log(probs.add(EPS), false);
        INDArray logP = oneHot.mul(logProbs).sum(1).reshape(labels.size(0), 1);

        // ratio r = exp(logP - oldLogP)
        INDArray ratio = Transforms.exp(logP.sub(oldLogP), false);

        // unclipped and clipped objectives
        INDArray unclipped = ratio.mul(adv);
        INDArray clippedRatio = ratio.dup();
        clippedRatio = Transforms.max(clippedRatio, 1.0 - clipEps);
        clippedRatio = Transforms.min(clippedRatio, 1.0 + clipEps);
        INDArray clipped = clippedRatio.mul(adv);

        INDArray surrogate = Transforms.min(unclipped, clipped);

        // entropy
        INDArray entropy = probs.mul(logProbs).sum(1).negi().reshape(labels.size(0), 1);

        // minimize: -(surrogate) - entropyCoef * entropy
        INDArray loss = surrogate.negi().sub(entropy.mul(entropyCoef));

        return loss; // [batch,1]
    }

    @Override
    public INDArray computeGradient(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask) {

        INDArray logits = preOutput.dup();
        INDArray legalMask = (mask == null) ? Nd4j.onesLike(logits) : mask.castTo(logits.dataType());

        INDArray probs = maskedSoftmax(logits, legalMask);

        // labels
        INDArray oneHot = labels.get(NDArrayIndex.all(), NDArrayIndex.interval(0, actionSize));
        INDArray adv = labels.get(NDArrayIndex.all(), NDArrayIndex.point(actionSize)).reshape(labels.size(0), 1);
        INDArray oldLogP = labels.get(NDArrayIndex.all(), NDArrayIndex.point(actionSize + 1)).reshape(labels.size(0), 1);

        INDArray logProbs = Transforms.log(probs.add(EPS), false);
        INDArray logP = oneHot.mul(logProbs).sum(1).reshape(labels.size(0), 1);

        INDArray ratio = Transforms.exp(logP.sub(oldLogP), false);

        // ---- determine if clipping is active (convert BOOL -> numeric) ----
        INDArray advPos = adv.gt(0.0).castTo(logits.dataType()); // [B,1] numeric 0/1
        INDArray advNeg = adv.lt(0.0).castTo(logits.dataType());

        INDArray clipHigh = ratio.gt(1.0 + clipEps).castTo(logits.dataType());
        INDArray clipLow  = ratio.lt(1.0 - clipEps).castTo(logits.dataType());

        INDArray clippedActive = advPos.mul(clipHigh).add(advNeg.mul(clipLow)); // numeric 0/1
        INDArray useUnclipped = Nd4j.onesLike(clippedActive).sub(clippedActive); // 1 if unclipped, 0 if clipped

        // d/d logP of (ratio * A) = ratio * A
        // loss is -surrogate, so gradient is negative
        INDArray dL_dLogP = ratio.mul(adv).mul(useUnclipped).negi(); // [B,1]

        // ---- gradient from surrogate through softmax logits ----
        // d log pi(a)/d logits = oneHot - probs
        INDArray gradFromSurrogate = oneHot.sub(probs).muliColumnVector(dL_dLogP); // [B,actionSize]

        // ---- entropy bonus gradient ----
        // entropy H = -sum p log p
        // add to loss: -entropyCoef * H  => gradient = -entropyCoef * dH/dlogits
        INDArray entropyGradAtProb = logProbs.add(1.0).negi(); // dH/dp = -(log p + 1)
        INDArray entropyGradLogits = softmaxJacobianVectorProduct(probs, entropyGradAtProb);
        entropyGradLogits.muli(-entropyCoef);

        INDArray grad = gradFromSurrogate.addi(entropyGradLogits);

        // zero-out illegal action grads
        grad.muli(legalMask);

        // backprop through activation (identity) to get dL/dz
        Pair<INDArray, INDArray> pair = activationFn.backprop(preOutput, grad);
        return pair.getFirst();
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
        return "PPOLoss(actionSize=" + actionSize + ",clip=" + clipEps + ",ent=" + entropyCoef + ")";
    }

    // ---------- helpers ----------

    private static INDArray maskedSoftmax(INDArray logits, INDArray legalMask) {
        // ensure same dtype
        INDArray m = legalMask.castTo(logits.dataType());

        // illegal -> very negative
        INDArray masked = logits.mul(m).add(m.rsub(1.0).mul(-1e9));

        // stable softmax
        INDArray max = masked.max(1);
        INDArray shifted = masked.subColumnVector(max);

        INDArray exp = Transforms.exp(shifted, false).mul(m);
        INDArray sum = exp.sum(1).add(EPS);

        return exp.divColumnVector(sum);
    }

    private static INDArray softmaxJacobianVectorProduct(INDArray probs, INDArray vec) {
        // Computes J_softmax^T * vec for each row.
        // For softmax: J = diag(p) - p p^T
        // So J^T * v = p ⊙ v - p * (p·v)
        INDArray pv = probs.mul(vec);                 // p ⊙ v
        INDArray dot = pv.sum(1).reshape(probs.size(0), 1); // (p·v)
        return pv.sub(probs.mulColumnVector(dot));
    }
}