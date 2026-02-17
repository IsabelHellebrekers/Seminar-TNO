import DataUtils.InstanceCreator;
import DataUtils.OutputCreator;
import Deterministic.CapacitatedResupplyMILP;
import Objects.*;
import java.util.*;

import java.io.IOException;
import java.util.Map;

import Stochastic.OutOfSampleMILP;

public class Main {
    public static void main(String[] args) throws IOException {
       InstanceCreator ic = new InstanceCreator();

    // Solve FD Concept (Deterministic MILP)
//        List<Result> results = CapacitatedResupplyMILP.solveInstances(new InstanceCreator().createFDInstance());

    // Solve Dispersed Concept (Deterministic MILP for contiguous partitions)
    //    List<Result> results = CapacitatedResupplyMILP.solveInstances(new InstanceCreator().contiguousPartitions());
    //    new OutputCreator().createCSV(results);

        // Out of Sample testing
        // OutOfSampleMILP.solveInstances(InstanceCreator.createFDInstance(), 200.0, Map.of("FSC_1", 50.0, "FSC_2", 50.0));
    }
}
