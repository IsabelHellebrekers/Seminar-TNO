import DataUtils.InstanceCreator;
import Objects.CCLpackage;
import Objects.OperatingUnit;
import Objects.Instance;
import Objects.FSC;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import com.gurobi.gurobi.*;

import Models.CapacitatedResupplyMILP;

public class Main {
    public static void main(String[] args) throws IOException {
        InstanceCreator ic = new InstanceCreator();
       Instance instance = ic.createFDInstance();
       solve(instance);

        // ic.generateInstances();
    }
    /**
     * Builds and solves the MILP and exports CSV outputs if the solution is optimal.
     * @param inst       parsed instance
     */
    private static void solve(Instance inst) {
        GRBEnv env = null;
        CapacitatedResupplyMILP milp = null;

        try {
            env = createGurobiEnv("gurobi.log");

            milp = new CapacitatedResupplyMILP(inst, env);
            GRBModel m = milp.getModel();

            milp.solve();

            printTruckVariables(m);

            int status = m.get(GRB.IntAttr.Status);
            System.out.println("\nMILP solve finished. Status code = " + status);

            if (status == GRB.Status.OPTIMAL) {
                System.out.println("  Objective = " + m.get(GRB.DoubleAttr.ObjVal));
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
        System.out.println("K_FSC_1           = " + m.getVarByName("K_FSC_1").get(GRB.DoubleAttr.X));
        System.out.println("K_FSC_2           = " + m.getVarByName("K_FSC_2").get(GRB.DoubleAttr.X));
    }
}
