package Analysis;

import DataUtils.InstanceCreator;
import Deterministic.CapacitatedResupplyMILP;
import Objects.CCLPackage;
import Objects.Instance;
import Stochastic.EvaluationHeuristic;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBEnv;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBModel;

import java.util.*;
import java.io.*;

public final class ExtendedTimeHorizonAnalysis {

    private ExtendedTimeHorizonAnalysis() {
    }

    private static final int[] DETERMINISTIC_HORIZONS = { 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24 };
    private static final int[] STOCHASTIC_EVAL_HORIZONS = { 20 };

    private static final double TIME_LIMIT_SECONDS = 300.0;

    private static final int OOS_SEED = 20000;
    private static final int N_SCENARIOS = 10000;

    private static final EvaluationHeuristic.TargetWeights OU_WEIGHTS = new EvaluationHeuristic.TargetWeights(1.2, 0.6,
            0.9);

    private static final EvaluationHeuristic.TargetWeights VUST_WEIGHTS = new EvaluationHeuristic.TargetWeights(1.0,
            1.0, 1.0);

    public static void run(List<InstanceConfig> configs) {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("EXTENDED TIME HORIZON ANALYSIS");
        System.out.println("============================================================");

        // runDeterministicExtendedHorizon();
        runStochasticExtendedHorizon(configs);
    }

    private static void runDeterministicExtendedHorizon() {
        System.out.println();
        System.out.println("------------------------------------------------------------");
        System.out.println("DETERMINISTIC EXTENDED HORIZON (FD)");
        System.out.println("------------------------------------------------------------");
        System.out.printf("%-8s %-18s %-12s %-14s %-14s %-10s%n",
                "H", "Status", "Runtime(s)", "UB / Obj", "LB", "Gap(%)");

        for (int horizon : DETERMINISTIC_HORIZONS) {
            Instance inst = InstanceCreator.createFDInstance(horizon).get(0);
            DeterministicRunSummary summary = solveDeterministicInstance(inst, TIME_LIMIT_SECONDS);

            if (!summary.hasIncumbent) {
                System.out.printf("%-8d %-18s %-12.2f %-14s %-14s %-10s%n",
                        summary.horizon,
                        summary.statusName,
                        summary.runtimeSeconds,
                        "no incumbent",
                        formatDouble(summary.lowerBound),
                        "-");
            } else {
                System.out.printf("%-8d %-18s %-12.2f %-14s %-14s %-10s%n",
                        summary.horizon,
                        summary.statusName,
                        summary.runtimeSeconds,
                        formatDouble(summary.upperBound),
                        formatDouble(summary.lowerBound),
                        formatGapPercent(summary.gap));
            }
        }
    }

    private static void runStochasticExtendedHorizon(List<InstanceConfig> configs) {
        System.out.println();
        System.out.println("------------------------------------------------------------");
        System.out.println("STOCHASTIC EXTENDED HORIZON");
        System.out.println("------------------------------------------------------------");

        EvaluationHeuristic.WeightConfig weightCfg = new EvaluationHeuristic.WeightConfig(OU_WEIGHTS, VUST_WEIGHTS);

        System.out.println("OU weights   = " + OU_WEIGHTS);
        System.out.println("VUST weights = " + VUST_WEIGHTS);
        System.out.println("OOS seed     = " + OOS_SEED);
        System.out.println("Scenarios    = " + N_SCENARIOS);

        for (InstanceConfig cfg : configs) {

            CCLPackage ccl4 = cfg.fourthCCL();

            for (int horizon : STOCHASTIC_EVAL_HORIZONS) {

                Instance evalInst;
                if (ccl4 != null) {
                    evalInst = InstanceCreator.createFDInstanceExtraType(
                            (int) ccl4.foodWaterKg,
                            (int) ccl4.fuelKg,
                            (int) ccl4.ammoKg,
                            horizon).get(0);
                } else {
                    evalInst = InstanceCreator.createFDInstance(horizon).get(0);
                }

                String csvFile = "stochastic_extended_horizon_H" + horizon + ".csv";

                int scenariosWithoutStockout = 0;
                int scenariosWithStockout = 0;
                double sumTotalStockoutKg = 0.0;

                try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(csvFile)))) {

                    writer.println(
                            "has_stockout,total_stockout_kg,first_stockout_day,number_of_stockout_days,"
                                    + "fw_stockout_days,fuel_stockout_days,ammo_stockout_days,"
                                    + "total_fw_stockout_kg,total_fuel_stockout_kg,total_ammo_stockout_kg");

                    for (int s = 1; s <= N_SCENARIOS; s++) {

                        long scenarioSeed = OOS_SEED + s;

                        EvaluationHeuristic.ScenarioResult result = EvaluationHeuristic.evaluateSingleScenario(
                                evalInst,
                                cfg.mscTrucks(),
                                cfg.trucksPerFSC(),
                                scenarioSeed,
                                weightCfg,
                                List.of(0.0, 0.0, 0.0, 0.0));

                        if (result.hasStockout) {

                            scenariosWithStockout++;

                            writer.printf(Locale.US,
                                    "%s,%.6f,%d,%d,%d,%d,%d,%.6f,%.6f,%.6f%n",
                                    result.hasStockout,
                                    result.totalStockoutKg,
                                    result.firstStockoutDay,
                                    result.numberOfStockoutDays,
                                    result.fwStockoutDays,
                                    result.fuelStockoutDays,
                                    result.ammoStockoutDays,
                                    result.totalFwStockoutKg,
                                    result.totalFuelStockoutKg,
                                    result.totalAmmoStockoutKg);

                        } else {
                            scenariosWithoutStockout++;
                        }

                        sumTotalStockoutKg += result.totalStockoutKg;
                    }

                    double avgTotalStockoutKg = sumTotalStockoutKg / N_SCENARIOS;
                    double noStockoutPct = 100.0 * scenariosWithoutStockout / N_SCENARIOS;

                    System.out.println("Written: " + csvFile);
                    System.out.printf(
                            "H=%d | no stockout=%d | with stockout=%d | no-stockout%%=%.2f | avg total stockout kg=%.2f%n",
                            horizon,
                            scenariosWithoutStockout,
                            scenariosWithStockout,
                            noStockoutPct,
                            avgTotalStockoutKg);

                } catch (IOException e) {
                    throw new RuntimeException("Failed to write CSV for H=" + horizon, e);
                }
            }
        }
    }

    private static DeterministicRunSummary solveDeterministicInstance(Instance inst, double timeLimitSeconds) {
        GRBEnv env = null;
        CapacitatedResupplyMILP milp = null;

        try {
            env = new GRBEnv(true);
            env.set(GRB.IntParam.OutputFlag, 0);
            env.set(GRB.IntParam.LogToConsole, 0);
            env.start();

            milp = new CapacitatedResupplyMILP(inst, env, false);
            GRBModel model = milp.getModel();
            model.set(GRB.DoubleParam.TimeLimit, timeLimitSeconds);

            milp.solve();

            int status = model.get(GRB.IntAttr.Status);
            String statusName = statusToString(status);
            double runtime = model.get(GRB.DoubleAttr.Runtime);
            int solCount = model.get(GRB.IntAttr.SolCount);

            boolean hasIncumbent = solCount > 0;

            Double upperBound = null;
            Double lowerBound = null;
            Double gap = null;

            if (hasIncumbent) {
                upperBound = model.get(GRB.DoubleAttr.ObjVal);
            }

            lowerBound = model.get(GRB.DoubleAttr.ObjBound);

            if (hasIncumbent && upperBound != null && lowerBound != null && Math.abs(upperBound) > 1e-9) {
                gap = Math.max(0.0, (upperBound - lowerBound) / upperBound);
            }

            return new DeterministicRunSummary(
                    inst.timeHorizon,
                    status,
                    statusName,
                    runtime,
                    hasIncumbent,
                    upperBound,
                    lowerBound,
                    gap);

        } catch (GRBException e) {
            throw new RuntimeException("Deterministic solve failed for H=" + inst.timeHorizon + ": " + e.getMessage(),
                    e);
        } finally {
            if (milp != null) {
                try {
                    milp.dispose();
                } catch (Exception ignored) {
                }
            }
            if (env != null) {
                try {
                    env.dispose();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String statusToString(int status) {
        return switch (status) {
            case GRB.Status.OPTIMAL -> "OPTIMAL";
            case GRB.Status.TIME_LIMIT -> "TIME_LIMIT";
            case GRB.Status.INFEASIBLE -> "INFEASIBLE";
            case GRB.Status.INF_OR_UNBD -> "INF_OR_UNBD";
            case GRB.Status.UNBOUNDED -> "UNBOUNDED";
            case GRB.Status.INTERRUPTED -> "INTERRUPTED";
            default -> "STATUS_" + status;
        };
    }

    private static String formatDouble(Double x) {
        if (x == null)
            return "-";
        return String.format("%.2f", x);
    }

    private static String formatGapPercent(Double gap) {
        if (gap == null)
            return "-";
        return String.format("%.2f", 100.0 * gap);
    }

    private static final class DeterministicRunSummary {
        final int horizon;
        final String statusName;
        final double runtimeSeconds;
        final boolean hasIncumbent;
        final Double upperBound;
        final Double lowerBound;
        final Double gap;

        DeterministicRunSummary(int horizon,
                int statusCode,
                String statusName,
                double runtimeSeconds,
                boolean hasIncumbent,
                Double upperBound,
                Double lowerBound,
                Double gap) {
            this.horizon = horizon;
            this.statusName = statusName;
            this.runtimeSeconds = runtimeSeconds;
            this.hasIncumbent = hasIncumbent;
            this.upperBound = upperBound;
            this.lowerBound = lowerBound;
            this.gap = gap;
        }
    }
}