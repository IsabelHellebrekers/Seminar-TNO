import DataUtils.InstanceCreator;
import DataUtils.OutputCreator;
import Objects.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import Stochastic.OutOfSampleMILP;
import com.gurobi.gurobi.*;

import Deterministic.CapacitatedResupplyMILP;

public class Main {
    public static void main(String[] args) throws IOException {
//        InstanceCreator ic = new InstanceCreator();
//        List<Result> results = CapacitatedResupplyMILP.solveInstances(new InstanceCreator().createFDInstance());
//        List<Result> results = CapacitatedResupplyMILP.solveInstances(new InstanceCreator().contiguousPartitions());
//        new OutputCreator().createCSV(results);


        // Out of Sample testing
        OutOfSampleMILP.solveInstances(new InstanceCreator().createFDInstance(), 200.0, Map.of("FSC_1", 50.0, "FSC_2", 50.0));
    }
}
