package Stochastic;

import Deterministic.*;
import Objects.*;

import com.gurobi.gurobi.*;
import DataUtils.InstanceCreator;

import java.util.*;

/**
 * Perfect-hindsight feasibility benchmark.
 * 
 * For each sampled stochastic scenario, we solve the deterministic
 * CapacitatedResupplyMILP
 * with full knowledge of the realized demand path and record whether at least
 * one feasible solution exists.
 * 
 * Two benchmark variants are evaluated on the same scenario set:
 * (1) Unlimited fleet size: the MILP is allowed to choose fleet variables (M
 * and K) freely
 * (2) Fixed fleet size: fleet variables are fixed to provided values
 * 
 * This benchmark is used to sparate policy failure from structural
 * infeasibility;
 * if even perfect hindsight is infeasible, then no heuristic can avoid
 * stockouts.
 *
 * @author 621349it Ies Timmerarends
 * @author 612348ih Isabel Hellebrekers
 * @author 631426ls Lena Stiebing
 * @author 661267eb Eeke Bavelaar
 */
public class PerfectHindsight {

    /**
     * Run the perfect-hindsight feasibility check for a given base instance.
     *
     * @param baseInstance the base deterministic instance
     * @param numScenarios number of stochastic scenarios to evaluate
     * @param baseSeed     base seed for reproducible scenario generation
     * @param mscTrucks    fixed MSC fleet size for the fixed-fleet benchmark
     * @param fscTrucks    fixed FSC fleet sizes keyed by FSC name
     * @throws GRBException if Gurobi fails during scenario solving
     */
    public static void run(Instance baseInstance, int numScenarios, int baseSeed, int mscTrucks, Map<String, Integer> fscTrucks) throws GRBException {
        List<Scenario> scenarios = generateScenarios(baseInstance, numScenarios, baseSeed);
        runPerfectHindsightBenchmarks(scenarios, mscTrucks, fscTrucks, baseSeed);
    }

    /**
     * Standalone entry point for the perfect-hindsight feasibility check.
     * (1) Builds the base deterministic instance.
     * (2) Generates N stochastic demand scenarios using (baseSeed + s).
     * (3) Runs feasibility benchmarks for unlimited vs fixed fleet size.
     *
     * @param args command-line arguments (unused)
     * @throws GRBException if Gurobi fails during scenario solving
     */
    public static void main(String[] args) throws GRBException {
        final int numScenarios = 1000;
        final int baseSeed = 10042;
        final int mscTrucks = 79;
        final int fsc1Trucks = 48;
        final int fsc2Trucks = 35;

        Map<String, Integer> fscTrucks = new HashMap<>();
        fscTrucks.put("FSC_1", fsc1Trucks);
        fscTrucks.put("FSC_2", fsc2Trucks);

        Instance fdInstance = InstanceCreator.createFDInstance().get(0);

        List<Scenario> scenarios = generateScenarios(fdInstance, numScenarios, baseSeed);

        runPerfectHindsightBenchmarks(scenarios, mscTrucks, fscTrucks, baseSeed);
    }

    /**
     * Solve a perfect-hindsight MILP for each scenario and count feasibility outcomes.
     * A scenario is treated as feasible if the solver finds at least one integer solution
     * within the time limit.
     *
     * @param scenarios sampled scenario list
     * @param M         fixed MSC fleet size for the fixed-fleet benchmark
     * @param K         fixed FSC fleet sizes, keyed by FSC name
     * @param baseSeed  seed used for scenario generation (printed in the summary)
     */
    private static void runPerfectHindsightBenchmarks(
            List<Scenario> scenarios,
            int mscTrucks,
            Map<String, Integer> fscTrucks,
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

                    if (solCount > 0) {
                        feasibleUnlimited++;
                    } else {
                        if (status == GRB.Status.INFEASIBLE) {
                            infeasibleUnlimited++;
                        }
                    }
                } finally {
                    if (milp != null) {
                        milp.dispose();
                    }
                }

                if ((idx + 1) % 10 == 0) {
                    System.out.printf("Part (1) progress %d/%d | feas=%d | infeas=%d%n",
                            (idx + 1), scenarios.size(), feasibleUnlimited, infeasibleUnlimited);
                }
            }

            // ------ (2) Fixed fleet (same scenarios, but fix M and K) ------
            for (int idx = 0; idx < scenarios.size(); idx++) {
                Scenario sc = scenarios.get(idx);

                CapacitatedResupplyMILP milp = null;
                try {
                    milp = new CapacitatedResupplyMILP(sc.instance, env, false);

                    fixFleetSize(milp.getModel(), sc.instance, mscTrucks, fscTrucks);

                    GRBModel model = milp.getModel();

                    model.set(GRB.IntParam.SolutionLimit, 1);
                    model.set(GRB.IntParam.MIPFocus, 1);
                    model.set(GRB.DoubleParam.TimeLimit, 30.0);

                    milp.solve();

                    boolean ok = model.get(GRB.IntAttr.SolCount) > 0;

                    if (ok) {
                        feasibleFixed++;
                    } else {
                        infeasibleFixed++;
                    }
                } finally {
                    if (milp != null) {
                        milp.dispose();
                    }
                }

                if ((idx + 1) % 10 == 0) {
                    System.out.printf("Part (2) progress %d/%d | feas=%d | infeas=%d%n",
                            (idx + 1), scenarios.size(), feasibleFixed, infeasibleFixed);
                }
            }
        } catch (GRBException e) {
            throw new RuntimeException(e);
        } finally {
            if (env != null) {
                try {
                    env.dispose();
                } catch (GRBException e) {
                    e.printStackTrace();
                }
            }
        }

        int numScenarios = scenarios.size();
        System.out.println("====================================");
        System.out.println("Perfect hindsight feasibility check");
        System.out.println("N scenarios: " + numScenarios);
        System.out.println("baseSeed: " + baseSeed);
        System.out.println();

        System.out.println("(1) Unlimited fleet (MILP minimizes trucks):");
        System.out.printf("  feasible   = %d (%.2f%%)%n", feasibleUnlimited, 100.0 * feasibleUnlimited / numScenarios);
        System.out.printf("  infeasible = %d (%.2f%%)%n", infeasibleUnlimited, 100.0 * infeasibleUnlimited / numScenarios);

        System.out.println();

        System.out.println("(2) Fixed fleet size (given mscTrucks and fscTrucks):");
        System.out.printf("  feasible   = %d (%.2f%%)%n", feasibleFixed, 100.0 * feasibleFixed / numScenarios);
        System.out.printf("  infeasible = %d (%.2f%%)%n", infeasibleFixed, 100.0 * infeasibleFixed / numScenarios);
    }

    /**
     * Stores a single stochastic scenario.
     */
    private static final class Scenario {
        final Instance instance;

        Scenario(long seed, Instance instance) {
            this.instance = instance;
        }
    }

    /**
     * Generate N stochastic scenarios from a deterministic base instance.
     * Scenario s uses seed (baseSeed + s), ensuring reproducibility.
     *
     * @param base         deterministic base instance
     * @param numScenarios number of scenarios to generate
     * @param baseSeed     base seed for reproducible scenario generation
     * @return list of Scenario objects, each holding a stochastic demand instance
     */
    private static List<Scenario> generateScenarios(Instance base, int numScenarios, int baseSeed) {
        List<Scenario> scenarios = new ArrayList<>(numScenarios);
        for (int s = 1; s <= numScenarios; s++) {
            long scenarioSeed = baseSeed + s;
            Instance inst = buildScenario(base, scenarioSeed);
            scenarios.add(new Scenario(scenarioSeed, inst));
        }
        return scenarios;
    }

    /**
     * Build a scenario Instance by sampling a full demand path for each OU and
     * product.
     * 
     * @param base         base instance to copy structure/capacities from
     * @param scenarioSeed RNG seed for demand sampling
     * @return scenario instance with stochastic demand arrays
     */
    private static Instance buildScenario(Instance base, long scenarioSeed) {
        int timeHorizon = base.getTimeHorizon();
        Sampling sampler = new Sampling(scenarioSeed);

        Map<String, double[]> fw = new HashMap<>();
        Map<String, double[]> fuel = new HashMap<>();
        Map<String, double[]> ammo = new HashMap<>();

        for (OperatingUnit ou : base.getOperatingUnits()) {
            fw.put(ou.getName(), new double[timeHorizon]);
            fuel.put(ou.getName(), new double[timeHorizon]);
            ammo.put(ou.getName(), new double[timeHorizon]);
        }

        for (int t = 1; t <= timeHorizon; t++) {
            int idx = t - 1;
            for (OperatingUnit ou : base.getOperatingUnits()) {
                double dFW = sampler.uniform() * ou.getDailyFoodWaterKg();
                double dFUEL = sampler.binomial() * ou.getDailyFuelKg();
                double dAMMO = sampler.triangular() * ou.getDailyAmmoKg();

                fw.get(ou.getName())[idx] = dFW;
                fuel.get(ou.getName())[idx] = dFUEL;
                ammo.get(ou.getName())[idx] = dAMMO;
            }
        }

        List<OperatingUnit> scenarioOus = new ArrayList<>(base.getOperatingUnits().size());
        for (OperatingUnit ou : base.getOperatingUnits()) {
            scenarioOus.add(new OperatingUnit(
                    ou.getName(),
                    ou.getOuType(),
                    fw.get(ou.getName()),
                    fuel.get(ou.getName()),
                    ammo.get(ou.getName()),
                    ou.getMaxFoodWaterKg(),
                    ou.getMaxFuelKg(),
                    ou.getMaxAmmoKg(),
                    ou.getSource()));
        }

        List<FSC> scenarioFscs = new ArrayList<>(base.getFSCs().size());
        for (FSC f : base.getFSCs()) {
            Map<String, int[]> copy = new HashMap<>();
            for (Map.Entry<String, int[]> e : f.getInitialStorageLevels().entrySet()) {
                copy.put(e.getKey(), e.getValue().clone());
            }
            scenarioFscs.add(new FSC(f.getName(), f.getMaxStorageCapCcls(), copy));
        }

        return new Instance(scenarioOus, scenarioFscs, base.getTimeHorizon());
    }

    /**
     * Fix fleet-size decision variables in an existing Gurobi model by pinning
     * both bounds of M and each K variable to the provided values.
     *
     * @param model     Gurobi model containing fleet variables named "M" and "K_<FSCname>"
     * @param inst      instance used to enumerate FSCs
     * @param mscTrucks fixed MSC fleet size
     * @param fscTrucks fixed FSC fleet sizes keyed by FSC name
     * @throws GRBException if variable lookup or attribute updates fail
     */
    private static void fixFleetSize(GRBModel model, Instance inst, int mscTrucks, Map<String, Integer> fscTrucks) throws GRBException {
        GRBVar mVar = model.getVarByName("M");
        mVar.set(GRB.DoubleAttr.LB, mscTrucks);
        mVar.set(GRB.DoubleAttr.UB, mscTrucks);

        for (FSC f : inst.getFSCs()) {
            String varName = "K_" + f.getName();
            GRBVar kVar = model.getVarByName(varName);

            int val = (fscTrucks != null && fscTrucks.containsKey(f.getName())) ? fscTrucks.get(f.getName()) : 0;
            kVar.set(GRB.DoubleAttr.LB, val);
            kVar.set(GRB.DoubleAttr.UB, val);
        }

        model.update();
    }

}
