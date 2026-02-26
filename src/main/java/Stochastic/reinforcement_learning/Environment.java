package Stochastic.reinforcement_learning;

import Objects.CCLpackage;
import Objects.FSC;
import Objects.Instance;
import Objects.OperatingUnit;

import java.util.*;
import java.util.logging.Logger;

public final class Environment {

    private static final Logger LOG = Logger.getLogger(Environment.class.getName());

    private final Instance instance;
    private final ActionSpace actionSpace;
    private final DemandModel demandModel;

    private final int horizon;

    private final int maxMscTrucksPerDay;
    private final int[] maxFscTrucksPerDay; // per FSC

    private final int vustOuId;

    private final int[] parentFscOfOu;
    private final int[] ouTypeOfOuId;

    private State state;
    private Random rng;

    // Episode logging
    private int episodeCounter = 0;
    private double lastEpisodeTotalStockoutKg = 0.0;
    private int lastEpisodeDaysSurvived = 0;

    private final boolean throwOnIllegalAction;

    public Environment(Instance instance,
                       ActionSpace actionSpace,
                       DemandModel demandModel,
                       int maxMscTrucksPerDay,
                       int[] maxFscTrucksPerDay,
                       boolean throwOnIllegalAction) {

        this.instance = Objects.requireNonNull(instance);
        this.actionSpace = Objects.requireNonNull(actionSpace);
        this.demandModel = Objects.requireNonNull(demandModel);

        this.horizon = instance.timeHorizon;
        this.maxMscTrucksPerDay = maxMscTrucksPerDay;
        this.maxFscTrucksPerDay = Objects.requireNonNull(maxFscTrucksPerDay);

        if (maxFscTrucksPerDay.length != instance.FSCs.size()) {
            throw new IllegalArgumentException("maxFscTrucksPerDay.length must equal number of FSCs");
        }

        this.throwOnIllegalAction = throwOnIllegalAction;

        this.vustOuId = actionSpace.getVustOuId();

        parentFscOfOu = new int[instance.operatingUnits.size()];
        ouTypeOfOuId = new int[instance.operatingUnits.size()];

        Map<String, Integer> fscIndexByName = new HashMap<>();
        for (int f = 0; f < instance.FSCs.size(); f++) {
            fscIndexByName.put(instance.FSCs.get(f).FSCname, f);
        }

        for (int ou = 0; ou < instance.operatingUnits.size(); ou++) {
            OperatingUnit u = instance.operatingUnits.get(ou);

            if (ou == this.vustOuId) {
                this.parentFscOfOu[ou] = -1;
            } else {
                Integer fscIdx = fscIndexByName.get(u.source);
                if (fscIdx == null) {
                    throw new IllegalStateException("OU " + u.operatingUnitName + " has unknown FSC source: " + u.source);
                }
                this.parentFscOfOu[ou] = fscIdx;
            }

            ouTypeOfOuId[ou] = mapOuToTypeIndex(u);
        }
    }

    public State reset(long seed) {

        this.rng = new Random(seed);
        this.episodeCounter++;
        this.lastEpisodeTotalStockoutKg = 0.0;
        this.lastEpisodeDaysSurvived = 0;

        int numFsc = this.instance.FSCs.size();
        int numOu = this.instance.operatingUnits.size();
        int numCcl = this.instance.cclTypes.size();

        int[][][] fscCclByType = new int[numFsc][OuType.COUNT][numCcl];

        for (int f = 0; f < numFsc; f++) {
            FSC fsc = this.instance.FSCs.get(f);

            for (Map.Entry<String, int[]> e : fsc.initialStorageLevels.entrySet()) {
                String key = e.getKey();
                int[] counts = e.getValue();

                int type = OuType.fromString(key);
                if (type < 0) {
                    type = OuType.fromString(key.replace("_", ""));
                }
                if (type < 0) {
                    throw new IllegalStateException("Cannot parse OU type from FSC.initialStorageLevels key: " + key);
                }
                if (counts.length != numCcl) {
                    throw new IllegalStateException("initialStorageLevels[" + key + "] has length " + counts.length +
                            " but expected numCcl=" + numCcl);
                }
                for (int c = 0; c < numCcl; c++) {
                    fscCclByType[f][type][c] = counts[c];
                }
            }
        }

        double[][] ouKg = new double[numOu][3];
        for (int ou = 0; ou < numOu; ou++) {
            OperatingUnit u = this.instance.operatingUnits.get(ou);
            ouKg[ou][0] = u.maxFoodWaterKg;
            ouKg[ou][1] = u.maxFuelKg;
            ouKg[ou][2] = u.maxAmmoKg;
        }

        int[] fscTrucks = new int[numFsc];

        state = new State(
                1,
                State.Phase.DEMAND,
                0,
                fscTrucks,
                fscCclByType,
                ouKg
        );

        return state.deepCopy();
    }

    public StepResult step(int actionIndex) {
        if (this.state.getPhase() == State.Phase.DEMAND) {
            return stepDemand();
        }

        Action action = this.actionSpace.decode(actionIndex);

        if (!isFeasible(action)) {
            if (throwOnIllegalAction) {
                throw new IllegalStateException("The agent tries to make an illegal move: " + action +
                        " in phase=" + state.getPhase() + " day=" + state.getDay());
            } else {
                return new StepResult(this.state.deepCopy(), -100.0, true);
            }
        }

        if (action.getType() == Action.ActionType.STOP)
            return stepStop();

        applyShipment(action);

        return new StepResult(this.state.deepCopy(), 0.0, false);
    }

    private StepResult stepDemand() {

        boolean stockout = false;
        double stockoutAmountKg = 0.0;
        int stockoutOuCount = 0;

        int day = state.getDay();

        for (int ou = 0; ou < instance.operatingUnits.size(); ou++) {
            OperatingUnit unit = instance.operatingUnits.get(ou);

            DemandModel.Demand d = this.demandModel.sampleDemand(unit, day, rng);

            double[][] inv = state.getOuKg();

            boolean ouStockout = false;

            inv[ou][0] -= d.foodWaterKg();
            inv[ou][1] -= d.fuelKg();
            inv[ou][2] -= d.ammoKg();

            if (inv[ou][0] < 0) {
                stockout = true;
                stockoutAmountKg += (-inv[ou][0]);
                ouStockout = true;
                inv[ou][0] = 0;
            }
            if (inv[ou][1] < 0) {
                stockout = true;
                stockoutAmountKg += (-inv[ou][1]);
                ouStockout = true;
                inv[ou][1] = 0;
            }
            if (inv[ou][2] < 0) {
                stockout = true;
                stockoutAmountKg += (-inv[ou][2]);
                ouStockout = true;
                inv[ou][2] = 0;
            }

            if (ouStockout) {
                stockoutOuCount++;
            }
        }

        lastEpisodeTotalStockoutKg += stockoutAmountKg;

        int totalOus = instance.operatingUnits.size();
        int satisfiedOuCount = totalOus - stockoutOuCount;

        double survivalBonus = (double) day / horizon;
        double reward = survivalBonus * survivalBonus * 120.0 + satisfiedOuCount * satisfiedOuCount * 5;

        if (stockout) {
            lastEpisodeDaysSurvived = day;
            LOG.info("[Episode " + episodeCounter + "] STOCKOUT day=" + day +
                    " ouStockouts=" + stockoutOuCount);
            return new StepResult(state.deepCopy(), reward, true);
        }

        lastEpisodeDaysSurvived = day;

        if (day >= horizon) {
            LOG.info("[Episode " + episodeCounter + "] SUCCESS no stockout");
            return new StepResult(state.deepCopy(), reward + 10.0, true);
        }

        state.setPhase(State.Phase.FSC_TO_OU);

        int[] fscTrucks = state.getFscTrucksRemaining();
        for (int i = 0; i < fscTrucks.length; i++) {
            fscTrucks[i] = maxFscTrucksPerDay[i];
        }

        return new StepResult(state.deepCopy(), reward, false);
    }

    private StepResult stepStop() {

        if (state.getPhase() == State.Phase.FSC_TO_OU) {
            state.setPhase(State.Phase.MSC_TO_FSC);
            state.setMscTrucksRemaining(maxMscTrucksPerDay);
            return new StepResult(state.deepCopy(), 0.0, false);
        }

        if (state.getPhase() == State.Phase.MSC_TO_FSC) {
            state.setDay(state.getDay() + 1);
            state.setPhase(State.Phase.DEMAND);
            return new StepResult(state.deepCopy(), 0.0, false);
        }

        throw new IllegalStateException("STOP in wrong phase");
    }

    /**
     * Legal action mask for actor-critic with "use trucks unless impossible".
     *
     * STOP is only allowed when there is no other feasible (non-STOP) action.
     * This prevents early collapse into STOP and reduces training noise.
     */
    public boolean[] getLegalActionMask() {
        boolean[] mask = new boolean[actionSpace.size()];

        boolean anyNonStopFeasible = false;

        for (int i = 0; i < mask.length; i++) {
            Action a = actionSpace.decode(i);
            boolean feasible = isFeasible(a);
            mask[i] = feasible;

            if (feasible && a.getType() != Action.ActionType.STOP) {
                anyNonStopFeasible = true;
            }
        }

        int stopIdx = actionSpace.getStopIndex();

        // If there exists at least one shipment action, force the agent not to STOP.
        if (anyNonStopFeasible) {
            mask[stopIdx] = false;
        } else {
            // If nothing else is feasible, STOP must be possible to advance the phase/day.
            mask[stopIdx] = true;
        }

        return mask;
    }

    public float[] getCurrentObservationVector() {
        return state.toObservationVector(horizon);
    }

    private boolean isFeasible(Action a) {

        if (a.getType() == Action.ActionType.STOP)
            return true;

        if (state.getPhase() == State.Phase.FSC_TO_OU) {

            if (a.getType() != Action.ActionType.FSC_TO_OU)
                return false;

            int ou = a.getOuId();
            if (ou == vustOuId) return false;

            int fsc = parentFscOfOu[ou];
            if (fsc < 0) return false;

            if (state.getFscTrucksRemaining()[fsc] <= 0)
                return false;

            int type = ouTypeOfOuId[ou];
            int cclType = a.getCclType();

            if (state.getFscCclByType()[fsc][type][cclType] <= 0)
                return false;

            return ouHasCapacity(ou, cclType);
        }

        if (state.getPhase() == State.Phase.MSC_TO_FSC) {

            if (state.getMscTrucksRemaining() <= 0)
                return false;

            if (a.getType() == Action.ActionType.MSC_TO_FSC) {

                int fsc = a.getFscId();

                int total = 0;
                int[][] buckets = state.getFscCclByType()[fsc];
                for (int t = 0; t < buckets.length; t++) {
                    for (int c = 0; c < buckets[t].length; c++) {
                        total += buckets[t][c];
                    }
                }
                return total < instance.FSCs.get(fsc).maxStorageCapCcls;
            }

            if (a.getType() == Action.ActionType.MSC_TO_OU) {
                return a.getOuId() == vustOuId
                        && ouHasCapacity(vustOuId, a.getCclType());
            }
        }

        return false;
    }

    private boolean ouHasCapacity(int ou, int cclIdx) {

        OperatingUnit u = instance.operatingUnits.get(ou);
        CCLpackage ccl = instance.cclTypes.get(cclIdx);

        double[][] inv = state.getOuKg();

        if (inv[ou][0] + ccl.foodWaterKg > u.maxFoodWaterKg)
            return false;
        if (inv[ou][1] + ccl.fuelKg > u.maxFuelKg)
            return false;
        if (inv[ou][2] + ccl.ammoKg > u.maxAmmoKg)
            return false;

        return true;
    }

    private void applyShipment(Action a) {
        switch (a.getType()) {
            case FSC_TO_OU -> {
                int ou = a.getOuId();
                int fsc = this.parentFscOfOu[ou];
                int ccl = a.getCclType();
                int type = ouTypeOfOuId[ou];

                this.state.getFscTrucksRemaining()[fsc]--;
                this.state.getFscCclByType()[fsc][type][ccl]--;

                addCclToOu(ou, ccl);
            }

            case MSC_TO_FSC -> {
                this.state.setMscTrucksRemaining(this.state.getMscTrucksRemaining() - 1);

                int fsc = a.getFscId();
                int type = a.getOuType();
                int ccl = a.getCclType();

                this.state.getFscCclByType()[fsc][type][ccl]++;
            }

            case MSC_TO_OU -> {
                this.state.setMscTrucksRemaining(this.state.getMscTrucksRemaining() - 1);
                addCclToOu(this.vustOuId, a.getCclType());
            }

            default -> throw new IllegalStateException("Unexpected action: " + a);
        }
    }

    private void addCclToOu(int ou, int cclIdx) {

        CCLpackage ccl = instance.cclTypes.get(cclIdx);
        double[][] inv = state.getOuKg();

        inv[ou][0] += ccl.foodWaterKg;
        inv[ou][1] += ccl.fuelKg;
        inv[ou][2] += ccl.ammoKg;
    }

    private int mapOuToTypeIndex(OperatingUnit u) {
        String name = u.operatingUnitName;
        if (name == null) {
            throw new IllegalStateException("OperatingUnit has null operatingUnitName");
        }

        String norm = name.trim().toUpperCase(Locale.ROOT);

        if (norm.equals("VUST")) {
            return -1;
        }
        if (norm.startsWith("GN")) return OuType.GN.ordinal();
        if (norm.startsWith("AT")) return OuType.AT.ordinal();
        if (norm.startsWith("PAINF")) return OuType.PAINF.ordinal();

        int underscore = norm.indexOf('_');
        if (underscore > 0) {
            int t = OuType.fromString(norm.substring(0, underscore));
            if (t >= 0) return t;
        }

        throw new IllegalStateException("Cannot infer OU type from operatingUnitName=" + u.operatingUnitName);
    }

    public int getHorizon() {
        return horizon;
    }

    public int getEpisodeCounter() {
        return episodeCounter;
    }

    public double getLastEpisodeTotalStockoutKg() {
        return lastEpisodeTotalStockoutKg;
    }

    public int getLastEpisodeDaysSurvived() {
        return lastEpisodeDaysSurvived;
    }
}