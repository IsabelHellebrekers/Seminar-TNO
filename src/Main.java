import Objects.CCLpackage;
import Objects.Centre;
import Objects.OperatingUnit;
import Objects.Instance;
import Solution.ExtractSolution;
import Solution.ResupplySolution;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import com.gurobi.gurobi.*;

import Models.CapacitatedResupplyMILP;

public class Main {

    private static final Path DEFAULT_INPUT = Path.of("src/Case/TNO_data_Erasmus_case.txt");
    private static final Path DEFAULT_OUTPUT_DIR = Path.of("output");
    private static final int DEFAULT_H = 10;

    public static void main(String[] args) throws IOException {
        // 1) Read instance
        Instance inst = readInstance(DEFAULT_INPUT);

        // 2) Data summary
        // printDataSummary(inst);

        // 3) Solve + export
        // solveAndExport(inst, DEFAULT_H, DEFAULT_OUTPUT_DIR);
    }

    /**
     * Reads the instance file into an {@link Instance}.
     * @param path input file path
     * @return parsed instance
     * @throws IOException if reading fails
     */
    private static Instance readInstance(Path path) throws IOException {
        return DataUtils.ReadDataFile.read(path);
    }

    /**
     * Prints a human-readable summary of the parsed instance data.
     * Includes operating units, centre capacities, CCL contents, and initial storage levels.
     * @param inst parsed instance
     */
    private static void printDataSummary(Instance inst) {
        System.out.println("Operating units: " + inst.operatingUnits.size());
        for (OperatingUnit row : inst.operatingUnits) {
            System.out.println("  " + row);
        }

        System.out.println("\nSource capacities: " + inst.sourceCapacities.size());
        for (Centre row : inst.sourceCapacities.values()) {
            System.out.println("  " + row);
        }

        System.out.println("\nCCL contents: " + inst.cclContents.size());
        for (CCLpackage row : inst.cclContents.values()) {
            System.out.println("  " + row);
        }

        System.out.println("\nInitial storage levels:");
        for (Map.Entry<String, Map<String, int[]>> centreEntry : inst.initialStorageLevels.entrySet()) {
            System.out.println("  " + centreEntry.getKey());
            for (Map.Entry<String, int[]> unitEntry : centreEntry.getValue().entrySet()) {
                int[] levels = unitEntry.getValue();
                System.out.println("    " + unitEntry.getKey() +
                        " -> Type1=" + levels[0] +
                        ", Type2=" + levels[1] +
                        ", Type3=" + levels[2]);
            }
        }
    }

    /**
     * Builds and solves the MILP and exports CSV outputs if the solution is optimal.
     * @param inst       parsed instance
     * @param H          planning horizon in days
     * @param outputDir  output directory for CSV files
     */
    private static void solveAndExport(Instance inst, int H, Path outputDir) {
        GRBEnv env = null;
        CapacitatedResupplyMILP milp = null;

        try {
            env = createGurobiEnv("gurobi.log");

            milp = new CapacitatedResupplyMILP(inst, env, H);
            GRBModel m = milp.getModel();

            milp.solve();

            printTruckVariables(m);

            int status = m.get(GRB.IntAttr.Status);
            System.out.println("\nMILP solve finished. Status code = " + status);

            if (status == GRB.Status.OPTIMAL) {
                System.out.println("  Objective = " + m.get(GRB.DoubleAttr.ObjVal));

                ResupplySolution sol = ExtractSolution.extract(m, inst, H);
                ExtractSolution.writeCsvs(sol, inst, H, outputDir);

                System.out.println("Wrote CSVs to: " + outputDir.toAbsolutePath());
            } else {
                System.out.println("  Non-optimal status.");
            }

        } catch (GRBException e) {
            System.err.println("\nGurobi error during test:");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("\nError during test:");
            e.printStackTrace();
        } finally {
            // Gurobi objects must be disposed explicitly
            try { if (milp != null) milp.dispose(); } catch (Exception ignored) {}
            try { if (env != null) env.dispose(); } catch (Exception ignored) {}
        }
    }

    /**
     * Creates and starts a Gurobi environment.
     * @param logFileName name of the Gurobi log file
     * @return started GRBEnv
     * @throws GRBException if Gurobi initialization fails
     */
    private static GRBEnv createGurobiEnv(String logFileName) throws GRBException {
        GRBEnv env = new GRBEnv(true);
        env.set("logFile", logFileName);
        env.start();
        return env;
    }

    /**
     * Prints the truck decision variables (fleet size) from the solved model.
     * @param m solved model
     * @throws GRBException if reading variable values fails
     */
    private static void printTruckVariables(GRBModel m) throws GRBException {
        System.out.println("M  (MSC trucks)  = " + m.getVarByName("M").get(GRB.DoubleAttr.X));
        System.out.println("K_FSC1           = " + m.getVarByName("K_FSC1").get(GRB.DoubleAttr.X));
        System.out.println("K_FSC2           = " + m.getVarByName("K_FSC2").get(GRB.DoubleAttr.X));
    }
}
