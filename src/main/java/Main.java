import DataUtils.InstanceCreator;
import DataUtils.OutputCreator;
import Objects.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.gurobi.gurobi.*;

import Deterministic.CapacitatedResupplyMILP;

public class Main {
    public static void main(String[] args) throws IOException {
//        InstanceCreator ic = new InstanceCreator();
//        List<Result> results = CapacitatedResupplyMILP.solveInstances(new InstanceCreator().createFDInstance());
        List<Result> results = CapacitatedResupplyMILP.solveInstances(new InstanceCreator().contiguousPartitions());

        new OutputCreator().createCSV(results);
    }
}
