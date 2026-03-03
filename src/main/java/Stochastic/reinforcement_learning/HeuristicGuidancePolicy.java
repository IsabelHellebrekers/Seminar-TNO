package Stochastic.reinforcement_learning;

import Objects.CCLpackage;
import Objects.FSC;
import Objects.Instance;
import Objects.OperatingUnit;

import java.util.Random;

public final class HeuristicGuidancePolicy {

    private static final double EPS = 1e-9;
    private static final int IDX_FW = 0;
    private static final int IDX_FUEL = 1;
    private static final int IDX_AMMO = 2;

    private static final double MAX_MULT_FW = 1.2;
    private static final double MAX_MULT_FUEL = 2.5;
    private static final double MAX_MULT_AMMO = 2.0;

    private final Instance instance;
    private final ActionSpace actionSpace;
    private final int vustOuId;

    public HeuristicGuidancePolicy(Instance instance, ActionSpace actionSpace) {
        this.instance = instance;
        this.actionSpace = actionSpace;
        this.vustOuId = actionSpace.getVustOuId();
    }

    public int selectAction(State state, boolean[] legalMask, int stopIndex, Random rng) {
        double bestScore = Double.NEGATIVE_INFINITY;
        int bestAction = -1;
        int ties = 0;

        for (int a = 0; a < legalMask.length; a++) {
            if (!legalMask[a]) {
                continue;
            }
            Action action = actionSpace.decode(a);
            double score = scoreAction(state, action, stopIndex);

            if (score > bestScore + EPS) {
                bestScore = score;
                bestAction = a;
                ties = 1;
            } else if (Math.abs(score - bestScore) <= EPS) {
                ties++;
                if (rng.nextInt(ties) == 0) {
                    bestAction = a;
                }
            }
        }

        return bestAction < 0 ? stopIndex : bestAction;
    }

    private double scoreAction(State state, Action action, int stopIndex) {
        if (action.getType() == Action.ActionType.STOP) {
            return scoreStop(state, stopIndex);
        }

        return switch (action.getType()) {
            case FSC_TO_OU -> scoreFscToOu(state, action);
            case MSC_TO_OU -> scoreMscToOu(state, action);
            case MSC_TO_FSC -> scoreMscToFsc(state, action);
            default -> -1e9;
        };
    }

    private double scoreStop(State state, int stopIndex) {
        if (state.getPhase() == State.Phase.FSC_TO_OU) {
            boolean urgent = false;
            for (int ou = 0; ou < instance.operatingUnits.size(); ou++) {
                if (ou == vustOuId) {
                    continue;
                }
                OperatingUnit unit = instance.operatingUnits.get(ou);
                double[] inv = state.getOuKg()[ou];
                double[] target = computeOuTarget(unit);
                double deficit = totalNormalizedDeficit(inv, target);
                if (deficit > 0.2) {
                    urgent = true;
                    break;
                }
            }
            return urgent ? -0.4 : 0.2;
        }

        if (state.getPhase() == State.Phase.MSC_TO_FSC) {
            OperatingUnit vust = instance.operatingUnits.get(vustOuId);
            double[] inv = state.getOuKg()[vustOuId];
            double[] target = computeVustTarget(vust);
            double deficit = totalNormalizedDeficit(inv, target);
            return deficit > 0.2 ? -0.4 : 0.2;
        }

        return stopIndex;
    }

    private double scoreFscToOu(State state, Action action) {
        int ouId = action.getOuId();
        int cclId = action.getCclType();

        OperatingUnit ou = instance.operatingUnits.get(ouId);
        CCLpackage ccl = instance.cclTypes.get(cclId);
        double[] inv = state.getOuKg()[ouId];

        double[] dmax = new double[]{
                ou.dailyFoodWaterKg * MAX_MULT_FW,
                ou.dailyFuelKg * MAX_MULT_FUEL,
                ou.dailyAmmoKg * MAX_MULT_AMMO
        };

        double urgency = minCoverageRatio(inv, dmax);
        double deficitReduction = deficitReductionOu(inv, ccl, ou);

        return 2.0 * deficitReduction + (1.0 - Math.min(2.0, urgency));
    }

    private double scoreMscToOu(State state, Action action) {
        int ouId = action.getOuId();
        int cclId = action.getCclType();

        OperatingUnit ou = instance.operatingUnits.get(ouId);
        CCLpackage ccl = instance.cclTypes.get(cclId);
        double[] inv = state.getOuKg()[ouId];
        double[] target = computeVustTarget(ou);

        double before = totalNormalizedDeficit(inv, target);
        double after = totalNormalizedDeficit(add(inv, ccl), target);
        return before - after;
    }

    private double scoreMscToFsc(State state, Action action) {
        int fscId = action.getFscId();
        int ouType = action.getOuType();
        int cclType = action.getCclType();

        FSC fsc = instance.FSCs.get(fscId);
        String ouTypeName = OuType.values()[ouType].name();

        int current = state.getFscCclByType()[fscId][ouType][cclType];
        int[] initArr = fsc.initialStorageLevels.get(ouTypeName);
        int init = initArr == null ? 0 : initArr[cclType];
        double ratio = (double) current / Math.max(1, init);

        int total = 0;
        int[][] buckets = state.getFscCclByType()[fscId];
        for (int t = 0; t < buckets.length; t++) {
            for (int c = 0; c < buckets[t].length; c++) {
                total += buckets[t][c];
            }
        }
        double fullness = (double) total / Math.max(1, fsc.maxStorageCapCcls);

        return (1.0 - ratio) + 0.25 * (1.0 - fullness);
    }

    private static double[] computeOuTarget(OperatingUnit ou) {
        double targetFW = Math.min(ou.maxFoodWaterKg, Math.max(0.0, 1.1 * ou.dailyFoodWaterKg * MAX_MULT_FW));
        double targetFuel = Math.min(ou.maxFuelKg, Math.max(0.0, 0.9 * ou.dailyFuelKg * MAX_MULT_FUEL));
        double targetAmmo = Math.min(ou.maxAmmoKg, Math.max(0.0, 1.4 * ou.dailyAmmoKg * MAX_MULT_AMMO));
        return new double[]{targetFW, targetFuel, targetAmmo};
    }

    private static double[] computeVustTarget(OperatingUnit vust) {
        double targetFW = Math.min(vust.maxFoodWaterKg, vust.dailyFoodWaterKg * MAX_MULT_FW * 0.9);
        double targetFuel = Math.min(vust.maxFuelKg, vust.dailyFuelKg * MAX_MULT_FUEL * 0.9);
        double targetAmmo = Math.min(vust.maxAmmoKg, vust.dailyAmmoKg * MAX_MULT_AMMO * 0.9);
        return new double[]{targetFW, targetFuel, targetAmmo};
    }

    private static double deficitReductionOu(double[] inv, CCLpackage ccl, OperatingUnit ou) {
        double[] target = computeOuTarget(ou);
        double before = totalNormalizedDeficit(inv, target);
        double after = totalNormalizedDeficit(add(inv, ccl), target);
        return before - after;
    }

    private static double[] add(double[] inv, CCLpackage ccl) {
        return new double[]{
                inv[IDX_FW] + ccl.foodWaterKg,
                inv[IDX_FUEL] + ccl.fuelKg,
                inv[IDX_AMMO] + ccl.ammoKg
        };
    }

    private static double totalNormalizedDeficit(double[] level, double[] target) {
        return normalizedDeficit(level[IDX_FW], target[IDX_FW])
                + normalizedDeficit(level[IDX_FUEL], target[IDX_FUEL])
                + normalizedDeficit(level[IDX_AMMO], target[IDX_AMMO]);
    }

    private static double normalizedDeficit(double level, double target) {
        return Math.max(0.0, target - level) / Math.max(1.0, target);
    }

    private static double minCoverageRatio(double[] inv, double[] dmax) {
        double fw = inv[IDX_FW] / Math.max(1.0, dmax[IDX_FW]);
        double fuel = inv[IDX_FUEL] / Math.max(1.0, dmax[IDX_FUEL]);
        double ammo = inv[IDX_AMMO] / Math.max(1.0, dmax[IDX_AMMO]);
        return Math.min(fw, Math.min(fuel, ammo));
    }
}
