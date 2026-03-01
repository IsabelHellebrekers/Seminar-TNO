package Stochastic.reinforcement_learning.dqn_agent;

import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;

public final class MaskedDqnNetwork {

    private final MultiLayerNetwork net;
    private final int obsSize;
    private final int actionSize;

    public MaskedDqnNetwork(int obsSize, int actionSize, double learningRate, long seed) {
        this.obsSize = obsSize;
        this.actionSize = actionSize;

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(seed)
                .weightInit(WeightInit.XAVIER)
                .updater(new Adam(learningRate))
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .list()
                .layer(new DenseLayer.Builder()
                        .nIn(obsSize)
                        .nOut(256)
                        .activation(Activation.RELU)
                        .build())
                .layer(new DenseLayer.Builder()
                        .nIn(256)
                        .nOut(256)
                        .activation(Activation.RELU)
                        .build())
                .layer(new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                        .nIn(256)
                        .nOut(actionSize)
                        .activation(Activation.IDENTITY)
                        .build())
                .build();

        this.net = new MultiLayerNetwork(conf);
        this.net.init();
    }

    private MaskedDqnNetwork(MultiLayerNetwork net, int obsSize, int actionSize) {
        this.net = net;
        this.obsSize = obsSize;
        this.actionSize = actionSize;
    }

    public MaskedDqnNetwork copy() {
        MultiLayerNetwork clone = net.clone();
        return new MaskedDqnNetwork(clone, obsSize, actionSize);
    }

    public INDArray qValues(INDArray obsBatch) {
        return net.output(obsBatch, false);
    }

    public void fit(INDArray obsBatch, INDArray targetQBatch) {
        net.fit(new DataSet(obsBatch, targetQBatch));
    }

    public void copyParamsFrom(MaskedDqnNetwork src) {
        this.net.setParams(src.net.params().dup());
    }

    public void softUpdateFrom(MaskedDqnNetwork src, double tau) {
        if (tau <= 0.0 || tau > 1.0) {
            throw new IllegalArgumentException("tau must be in (0, 1].");
        }
        INDArray dstParams = this.net.params();
        INDArray srcParams = src.net.params();
        dstParams.muli(1.0 - tau).addi(srcParams.mul(tau));
    }

    public int getObsSize() {
        return obsSize;
    }

    public int getActionSize() {
        return actionSize;
    }
}
