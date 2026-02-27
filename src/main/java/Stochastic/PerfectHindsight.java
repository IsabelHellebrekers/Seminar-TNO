package Stochastic;

import Deterministic.*;
import Objects.*;

import com.gurobi.gurobi.*;
import DataUtils.InstanceCreator;

import java.util.*;

public class PerfectHindsight {

    public static void main(String[] args) {
        int N = 1000;
        int baseseed = 10042; // NEW SEED (ALSO USED FOR OOS RESULTS) - NEED TO RUN METHOD AGAIN

        int M = 78; // 78
        Map<String, Integer> K = new HashMap<>();
        K.put("FSC_1", 48); // 48
        K.put("FSC_2", 34); // 34

        Instance fdInstance = InstanceCreator.createFDInstance().get(0);

        List<Scenario> scenarios = generateScenarios(fdInstance, N, baseseed);

        runPerfectHindsightBenchmarks(scenarios, M, K, baseseed);
    }

    private static void runPerfectHindsightBenchmarks(
            List<Scenario> scenarios,
            int M,
            Map<String, Integer> K,
            int baseSeed) {
        GRBEnv env = null;

        // Non fixed fleet size
        int feasibleUnlimited = 0;
        int infeasibleUnlimited = 0;

        // Fixed fleet size
        int feasibleFixed = 0;
        int infeasibleFixed = 0;

        try {
            env = new GRBEnv(true);
            env.set(GRB.IntParam.OutputFlag, 0);
            env.set(GRB.IntParam.Threads, 1);
            env.start();

            // ------ (1) Unlimited fleet size (not fixed) ------
            for (int idx = 0; idx < scenarios.size(); idx++) {
                Scenario sc = scenarios.get(idx);

                CapacitatedResupplyMILP milp = null;
                try {
                    milp = new CapacitatedResupplyMILP(sc.instance, env, false);

                    GRBModel model = milp.getModel();
                    model.set(GRB.IntParam.SolutionLimit, 1);
                    model.set(GRB.IntParam.MIPFocus, 1);
                    model.set(GRB.DoubleParam.TimeLimit, 30.0);

                    milp.solve();

                    int status = model.get(GRB.IntAttr.Status);
                    int solCount = model.get(GRB.IntAttr.SolCount);

                    // Feasible = er is minstens 1 solution in de pool
                    if (solCount > 0) {
                        feasibleUnlimited++;
                    } else {
                        // Alleen "echt infeasible" tellen als infeasible als het bewezen is
                        if (status == GRB.Status.INFEASIBLE) {
                            infeasibleUnlimited++;
                        }
                    }
                } finally {
                    if (milp != null)
                        milp.dispose();
                }

                if ((idx + 1) % 10 == 0) {
                    System.out.printf("Part (1) progress %d/%d | feas=%d | infeas=%d%n",
                            (idx + 1), scenarios.size(), feasibleUnlimited, infeasibleUnlimited);
                }
            }

            // ------ (2) Fixed fleet (same scenarios, but fix M and K) ------
            // for (int idx = 0; idx < scenarios.size(); idx++) {
            // Scenario sc = scenarios.get(idx);

            // CapacitatedResupplyMILP milp = null;
            // try {
            // milp = new CapacitatedResupplyMILP(sc.instance, env, false);

            // fixFleetSize(milp.getModel(), sc.instance, M, K);

            // GRBModel model = milp.getModel();

            // model.set(GRB.IntParam.SolutionLimit, 1);
            // model.set(GRB.IntParam.MIPFocus, 1);

            // milp.solve();

            // boolean ok = model.get(GRB.IntAttr.SolCount) > 0;

            // if (ok) feasibleFixed++;
            // else {
            // infeasibleFixed++;
            // }
            // } finally {
            // if (milp != null) milp.dispose();
            // }

            // if ((idx + 1) % 10 == 0) {
            // System.out.printf("Part (2) progress %d/%d | feas=%d | infeas=%d%n",
            // (idx + 1), scenarios.size(), feasibleFixed, infeasibleFixed
            // );
            // }
            // }
        } catch (GRBException e) {
            throw new RuntimeException(e);
        } finally {
            if (env != null) {
                try {
                    env.dispose();
                } catch (Exception ignored) {
                }
            }
        }

        int N = scenarios.size();
        System.out.println("====================================");
        System.out.println("Perfect hindsight feasibility check");
        System.out.println("N scenarios: " + N);
        System.out.println("baseSeed: " + baseSeed);
        System.out.println();

        System.out.println("(1) Unlimited fleet (MILP minimizes trucks):");
        System.out.printf("  feasible   = %d (%.2f%%)%n", feasibleUnlimited, 100.0 * feasibleUnlimited / N);
        System.out.printf("  infeasible = %d (%.2f%%)%n", infeasibleUnlimited, 100.0 * infeasibleUnlimited / N);

        System.out.println();

        System.out.println("(2) Fixed fleet size (given M and K):");
        System.out.printf("  feasible   = %d (%.2f%%)%n", feasibleFixed, 100.0 * feasibleFixed / N);
        System.out.printf("  infeasible = %d (%.2f%%)%n", infeasibleFixed, 100.0 * infeasibleFixed / N);
    }

    private static final class Scenario {
        final long seed;
        final Instance instance;

        Scenario(long seed, Instance instance) {
            this.seed = seed;
            this.instance = instance;
        }
    }

    private static List<Scenario> generateScenarios(Instance base, int N, int baseseed) {
        List<Scenario> scenarios = new ArrayList<>(N);
        for (int s = 1; s <= N; s++) {
            long scenarioSeed = baseseed + s;
            Instance inst = buildScenario(base, scenarioSeed);
            scenarios.add(new Scenario(scenarioSeed, inst));
        }
        return scenarios;
    }

    private static Instance buildScenario(Instance base, long scenarioSeed) {
        int T = base.timeHorizon;
        Sampling sampler = new Sampling(scenarioSeed);

        Map<String, double[]> fw = new HashMap<>();
        Map<String, double[]> fuel = new HashMap<>();
        Map<String, double[]> ammo = new HashMap<>();

        for (OperatingUnit ou : base.operatingUnits) {
            fw.put(ou.operatingUnitName, new double[T]);
            fuel.put(ou.operatingUnitName, new double[T]);
            ammo.put(ou.operatingUnitName, new double[T]);
        }

        for (int t = 1; t <= T; t++) {
            int idx = t - 1;
            for (OperatingUnit ou : base.operatingUnits) {
                double dFW = sampler.uniform() * ou.dailyFoodWaterKg;
                double dFUEL = sampler.binomial() * ou.dailyFuelKg;
                double dAMMO = sampler.triangular() * ou.dailyAmmoKg;

                // double dFW = ou.dailyFoodWaterKg;
                // double dFUEL = ou.dailyFuelKg;
                // double dAMMO = ou.dailyAmmoKg;

                fw.get(ou.operatingUnitName)[idx] = dFW;
                fuel.get(ou.operatingUnitName)[idx] = dFUEL;
                ammo.get(ou.operatingUnitName)[idx] = dAMMO;
            }
        }

        List<OperatingUnit> scenarioOus = new ArrayList<>(base.operatingUnits.size());
        for (OperatingUnit ou : base.operatingUnits) {
            scenarioOus.add(new OperatingUnit(
                    ou.operatingUnitName,
                    ou.ouType,
                    fw.get(ou.operatingUnitName),
                    fuel.get(ou.operatingUnitName),
                    ammo.get(ou.operatingUnitName),
                    ou.maxFoodWaterKg,
                    ou.maxFuelKg,
                    ou.maxAmmoKg,
                    ou.source));
        }

        List<FSC> scenarioFscs = new ArrayList<>(base.FSCs.size());
        for (FSC f : base.FSCs) {
            Map<String, int[]> copy = new HashMap<>();
            for (Map.Entry<String, int[]> e : f.initialStorageLevels.entrySet()) {
                copy.put(e.getKey(), e.getValue().clone());
            }
            scenarioFscs.add(new FSC(f.FSCname, f.maxStorageCapCcls, copy));
        }

        return new Instance(scenarioOus, scenarioFscs);
    }

    private static void fixFleetSize(GRBModel model, Instance inst, int M, Map<String, Integer> K) throws GRBException {
        GRBVar mVar = model.getVarByName("M");
        mVar.set(GRB.DoubleAttr.LB, M);
        mVar.set(GRB.DoubleAttr.UB, M);

        for (FSC f : inst.FSCs) {
            String varName = "K_" + f.FSCname;
            GRBVar kVar = model.getVarByName(varName);

            int val = (K != null && K.containsKey(f.FSCname)) ? K.get(f.FSCname) : 0;
            kVar.set(GRB.DoubleAttr.LB, val);
            kVar.set(GRB.DoubleAttr.UB, val);
        }

        model.update();
    }

}
