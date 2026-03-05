package Stochastic.reinforcement_learning.ppo_agent;

import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.MultiDataSet;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;

public final class PPONetwork {

    private final ComputationGraph graph;
    private final int obsSize;
    private final int actionSize;

    public PPONetwork(int obsSize, int actionSize, double lr, double clipEps, double entropyCoef, long seed) {
        this.obsSize = obsSize;
        this.actionSize = actionSize;

        ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(seed)
                .weightInit(WeightInit.XAVIER)
                .updater(new Adam(lr))
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .graphBuilder()
                .addInputs("obs")
                .setInputTypes(InputType.feedForward(obsSize))

                .addLayer("dense1", new DenseLayer.Builder()
                        .nIn(obsSize).nOut(256)
                        .activation(Activation.RELU)
                        .build(), "obs")
                .addLayer("dense2", new DenseLayer.Builder()
                        .nIn(256).nOut(256)
                        .activation(Activation.RELU)
                        .build(), "dense1")

                // Policy logits (IDENTITY) with PPO loss
                .addLayer("policy_logits", new OutputLayer.Builder()
                        .nIn(256).nOut(actionSize)
                        .activation(Activation.IDENTITY)
                        .lossFunction(new PPOLoss(actionSize, clipEps, entropyCoef))
                        .build(), "dense2")

                // Value head
                .addLayer("value", new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                        .nIn(256).nOut(1)
                        .activation(Activation.IDENTITY)
                        .build(), "dense2")

                .setOutputs("policy_logits", "value")
                .build();

        this.graph = new ComputationGraph(conf);
        this.graph.init();
    }

    /** outputs[0]=policy logits [1,actionSize], outputs[1]=value [1,1] */
    public INDArray[] forward(INDArray obsRow) {
        return graph.output(false, obsRow);
    }

    /**
     * Fit with policy labels + value labels + policy label mask.
     * policyLabelMask must be [batch, actionSize] (1 legal, 0 illegal)
     */
    public void fit(INDArray obsBatch,
                    INDArray policyLabels,
                    INDArray valueLabels,
                    INDArray policyLabelMask) {

        MultiDataSet mds = new MultiDataSet(
                new INDArray[]{obsBatch},
                new INDArray[]{policyLabels, valueLabels},
                null,
                new INDArray[]{policyLabelMask, null}
        );
        graph.fit(mds);
    }

    public ComputationGraph getGraph() { return graph; }
    public int getObsSize() { return obsSize; }
    public int getActionSize() { return actionSize; }
}