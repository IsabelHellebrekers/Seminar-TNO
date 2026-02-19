import DataUtils.InstanceCreator;
import Objects.Instance;

import java.io.IOException;

import Stochastic.general_approach.OutOfSampleSolver;

public class Main {
    public static void main(String[] args) throws IOException {

        InstanceCreator ic = new InstanceCreator();
        Instance base = ic.createFDInstance().get(0);

        // Run out-of-sample evaluation
        int nSamples = 100;
        long baseSeed = 12345L;

        OutOfSampleSolver oos = new OutOfSampleSolver(nSamples, baseSeed);
        oos.run(base);
    }
}