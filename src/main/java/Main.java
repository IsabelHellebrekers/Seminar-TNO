import DataUtils.InstanceCreator;
import DataUtils.OutputCreator;
import Deterministic.CapacitatedResupplyMILP;
import Stochastic.*;
import Objects.*;
import java.util.*;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        InstanceCreator ic = new InstanceCreator();

        // Solve FD Concept (Deterministic MILP)
        // List<Result> results = CapacitatedResupplyMILP.solveInstances(new
        // InstanceCreator().createFDInstance());

        // Solve Dispersed Concept (Deterministic MILP for contiguous partitions)
        // List<Result> results = CapacitatedResupplyMILP.solveInstances(new
        // InstanceCreator().contiguousPartitions());
        // new OutputCreator().createCSV(results);

        // Out of Sample testing
        // OutOfSampleMILP.solveInstances(InstanceCreator.createFDInstance(), 200.0,
        // Map.of("FSC_1", 50.0, "FSC_2", 50.0));

        // Evaluation Heuristic for FD concept
        List<Instance> instances = ic.createFDInstance();
        Instance data = instances.get(0);

        // Random fleet size
        int M = 76; // 76
        Map<String, Integer> K = new HashMap<>();
        K.put("FSC_1", 47); // 47
        K.put("FSC_2", 33); // 33

        int nScenarios = 1000;
        int baseSeed = 42; 
        EvaluationReporter.reportStockouts(data, M, K, nScenarios, baseSeed);
    }
}
