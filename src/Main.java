import Objects.CCLpackage;
import Objects.Centre;
import Objects.OperatingUnit;
import Objects.instance;
import Solution.ExtractSolution;
import Solution.ResupplySolution;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import com.gurobi.gurobi.*;

import Models.CapacitatedResupplyMILP;

public class Main {
    public static void main(String[] args) throws IOException {
        Path path = Path.of("src/Case/TNO_data_Erasmus_case.txt");
        instance inst = DataUtils.ReadDataFile.read(path);

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

        GRBEnv env = null;
        CapacitatedResupplyMILP milp = null;

        try {
            env = new GRBEnv(true);
            env.set("logFile", "gurobi.log");
            env.start();

            int H = 10;
            milp = new CapacitatedResupplyMILP(inst, env, H);

            GRBModel m = milp.getModel();
            System.out.println("\nMILP built:");
            System.out.println("  Vars        = " + m.get(GRB.IntAttr.NumVars));
            System.out.println("  Constraints = " + m.get(GRB.IntAttr.NumConstrs));

            milp.solve();

            for (String w : new String[]{"FSC 1", "FSC 2"}) {
                double totalT1 = 0.0;
                for (String c : inst.cclContents.keySet()) {
                    for (Objects.OuType o : Objects.OuType.values()) {
                        String vn = "S_" + w.replace(" ", "") + "_" + c.replace(" ", "") + "_" + o + "_t1";
                        totalT1 += m.getVarByName(vn).get(GRB.DoubleAttr.X);
                    }
                }
                System.out.println("DEBUG: total S at " + w + " day 1 = " + totalT1);
            }

            System.out.println("M  (MSC trucks)  = " + m.getVarByName("M").get(GRB.DoubleAttr.X));
            System.out.println("K_FSC1           = " + m.getVarByName("K_FSC1").get(GRB.DoubleAttr.X));
            System.out.println("K_FSC2           = " + m.getVarByName("K_FSC2").get(GRB.DoubleAttr.X));

            int status = m.get(GRB.IntAttr.Status);
            System.out.println("\nMILP solve finished. Status code = " + status);

            if (status == GRB.Status.OPTIMAL) {
                System.out.println("  Objective = " + m.get(GRB.DoubleAttr.ObjVal));

                ResupplySolution sol = ExtractSolution.extract(m, inst, H);
                Path outputDir = Path.of("output");
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
            try { if (milp != null) milp.dispose(); } catch (Exception ignored) {}
            try { if (env != null) env.dispose(); } catch (Exception ignored) {}
        }
    }   
}
