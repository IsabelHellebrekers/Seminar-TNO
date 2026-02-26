package Stochastic.reinforcement_learning.actor_critic_agent;

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

public final class ActorCriticNetwork {

    private final ComputationGraph graph;
    private final int obsSize;
    private final int actionSize;

    public ActorCriticNetwork(int obsSize, int actionSize, double learningRate) {
        this.obsSize = obsSize;
        this.actionSize = actionSize;

        ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(123)
                .weightInit(WeightInit.XAVIER)
                .updater(new Adam(learningRate))
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .graphBuilder()
                .addInputs("obs")
                .setInputTypes(InputType.feedForward(obsSize))

                // Shared trunk
                .addLayer("dense1", new DenseLayer.Builder()
                        .nIn(obsSize)
                        .nOut(256)
                        .activation(Activation.RELU)
                        .build(), "obs")
                .addLayer("dense2", new DenseLayer.Builder()
                        .nIn(256)
                        .nOut(256)
                        .activation(Activation.RELU)
                        .build(), "dense1")

                // Policy head: softmax over actions, custom loss
                .addLayer("policy", new OutputLayer.Builder()
                        .nIn(256)
                        .nOut(actionSize)
                        .activation(Activation.SOFTMAX)
                        .lossFunction(new AdvantageWeightedSoftmaxLoss())
                        .build(), "dense2")

                // Value head: scalar
                .addLayer("value", new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                        .nIn(256)
                        .nOut(1)
                        .activation(Activation.IDENTITY)
                        .build(), "dense2")

                .setOutputs("policy", "value")
                .build();

        this.graph = new ComputationGraph(conf);
        this.graph.init();
    }

    /**
     * @return outputs[0] = policy probabilities [1, actionSize], outputs[1] = value [1,1]
     */
    public INDArray[] forward(INDArray obsRow) {
        return graph.output(false, obsRow);
    }

    public void fit(INDArray obsBatch, INDArray policyLabelBatch, INDArray valueLabelBatch) {
        MultiDataSet mds = new MultiDataSet(
                new INDArray[]{obsBatch},
                new INDArray[]{policyLabelBatch, valueLabelBatch}
        );
        graph.fit(mds);
    }

    public ComputationGraph getGraph() {
        return graph;
    }

    public int getObsSize() {
        return obsSize;
    }

    public int getActionSize() {
        return actionSize;
    }
}