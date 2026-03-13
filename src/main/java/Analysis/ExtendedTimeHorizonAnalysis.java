package Analysis;

import DataUtils.InstanceCreator;
import Deterministic.CapacitatedResupplyMILP;
import Objects.Instance;
import Stochastic.EvaluationHeuristic;
import Stochastic.EvaluationHeuristic.EvaluationSummary;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBEnv;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBModel;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

public final class ExtendedTimeHorizonAnalysis {

    private ExtendedTimeHorizonAnalysis() {
    }

    private static final int[] DETERMINISTIC_HORIZONS = {2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24};
    private static final int[] STOCHASTIC_EVAL_HORIZONS = {100};

    private static final double TIME_LIMIT_SECONDS = 300.0;

    private static final int MSC_TRUCKS = 127;
    private static final int FSC1_TRUCKS = 63;
    private static final int FSC2_TRUCKS = 45;

    private static final int CCL4_FW = 2000;
    private static final int CCL4_FUEL = 6000;
    private static final int CCL4_AMMO = 2000;

    private static final int OOS_SEED = 10042;
    private static final int N_SCENARIOS = 1000;

    private static final EvaluationHeuristic.TargetWeights OU_WEIGHTS =
            new EvaluationHeuristic.TargetWeights(1.3, 0.7, 1.3);

    private static final EvaluationHeuristic.TargetWeights VUST_WEIGHTS =
            new EvaluationHeuristic.TargetWeights(1.0, 1.0, 1.2);

    public static void run() {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("EXTENDED TIME HORIZON ANALYSIS");
        System.out.println("============================================================");

        runDeterministicExtendedHorizon();
        runStochasticExtendedHorizon();
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

    private static void runStochasticExtendedHorizon() {
        System.out.println();
        System.out.println("------------------------------------------------------------");
        System.out.println("STOCHASTIC EXTENDED HORIZON");
        System.out.println("------------------------------------------------------------");

        int M = MSC_TRUCKS;
        Map<String, Integer> K = new HashMap<>();
        K.put("FSC_1", FSC1_TRUCKS);
        K.put("FSC_2", FSC2_TRUCKS);

        EvaluationHeuristic.WeightConfig cfg =
                new EvaluationHeuristic.WeightConfig(OU_WEIGHTS, VUST_WEIGHTS);

        System.out.println("Fixed fleet and heuristic settings:");
        System.out.println("MSC trucks = " + M);
        System.out.println("FSC_1 trucks = " + K.get("FSC_1"));
        System.out.println("FSC_2 trucks = " + K.get("FSC_2"));
        System.out.println("CCL4 = (" + CCL4_FW + " FW, " + CCL4_FUEL + " FUEL, " + CCL4_AMMO + " AMMO)");
        System.out.println("OU weights   = " + OU_WEIGHTS);
        System.out.println("VUST weights = " + VUST_WEIGHTS);
        System.out.println("OOS seed     = " + OOS_SEED);
        System.out.println("Scenarios    = " + N_SCENARIOS);

        System.out.println();
        System.out.printf("%-8s %-18s %-22s %-22s%n",
                "H_eval", "No-stockout(%)", "Scenarios w/o stockout", "Avg total stockout kg");

        for (int horizon : STOCHASTIC_EVAL_HORIZONS) {
            Instance evalInst = InstanceCreator
                    .createFDInstanceExtraType(CCL4_FW, CCL4_FUEL, CCL4_AMMO, horizon)
                    .get(0);

            EvaluationSummary summary = EvaluationHeuristic.evaluate(
                    evalInst, M, K, N_SCENARIOS, OOS_SEED, cfg, List.of(0.0, 0.0, 0.0, 0.0));

            System.out.printf("%-8d %-18.2f %-22d %-22.2f%n",
                    horizon,
                    summary.noStockoutPercentage,
                    summary.scenariosWithoutStockout,
                    summary.avgTotalStockoutKg);
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
            throw new RuntimeException("Deterministic solve failed for H=" + inst.timeHorizon + ": " + e.getMessage(), e);
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
        if (x == null) {
            return "-";
        }
        return String.format("%.2f", x);
    }

    private static String formatGapPercent(Double gap) {
        if (gap == null) {
            return "-";
        }
        return String.format("%.2f", 100.0 * gap);
    }

    private static final class DeterministicRunSummary {
        final int horizon;
        final int statusCode;
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
            this.statusCode = statusCode;
            this.statusName = statusName;
            this.runtimeSeconds = runtimeSeconds;
            this.hasIncumbent = hasIncumbent;
            this.upperBound = upperBound;
            this.lowerBound = lowerBound;
            this.gap = gap;
        }
    }
}