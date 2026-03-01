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
    private final boolean forceUseAllTrucksInPhase;

    private final int vustOuId;

    private final int[] parentFscOfOu;
    private final int[] ouTypeOfOuId;

    private State state;
    private Random rng;

    // Episode logging
    private int episodeCounter = 0;
    private double lastEpisodeTotalStockoutKg = 0.0;
    private int lastEpisodeDaysSurvived = 0;

    public Environment(Instance instance,
            ActionSpace actionSpace,
            DemandModel demandModel,
            int maxMscTrucksPerDay,
            int[] maxFscTrucksPerDay) {
        this(instance, actionSpace, demandModel, maxMscTrucksPerDay, maxFscTrucksPerDay, true);
    }

    public Environment(Instance instance,
            ActionSpace actionSpace,
            DemandModel demandModel,
            int maxMscTrucksPerDay,
            int[] maxFscTrucksPerDay,
            boolean forceUseAllTrucksInPhase) {

        this.instance = instance;
        this.actionSpace = actionSpace;
        this.demandModel = demandModel;

        this.horizon = instance.timeHorizon;
        this.maxMscTrucksPerDay = maxMscTrucksPerDay;
        this.maxFscTrucksPerDay = maxFscTrucksPerDay;
        this.forceUseAllTrucksInPhase = forceUseAllTrucksInPhase;

        if (maxFscTrucksPerDay.length != instance.FSCs.size()) {
            throw new IllegalArgumentException("maxFscTrucksPerDay.length must equal number of FSCs");
        }

        this.vustOuId = actionSpace.getVustOuId();

        // parentFscOfOu & ouTypeOfOuId
        parentFscOfOu = new int[instance.operatingUnits.size()];
        ouTypeOfOuId = new int[instance.operatingUnits.size()];

        Map<String, Integer> fscIndexByName = new HashMap<>();
        for (int fscIdx = 0; fscIdx < instance.FSCs.size(); fscIdx++) {
            fscIndexByName.put(instance.FSCs.get(fscIdx).FSCname, fscIdx);
        }

        for (int ou = 0; ou < instance.operatingUnits.size(); ou++) {
            OperatingUnit u = instance.operatingUnits.get(ou);

            if (ou == this.vustOuId) {
                this.parentFscOfOu[ou] = -1;
            } else {
                Integer fscIdx = fscIndexByName.get(u.source);
                if (fscIdx == null) {
                    throw new IllegalStateException(
                            "OU " + u.operatingUnitName + " has unknown FSC source: " + u.source);
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

        // initial full inventory fscCclByType
        int[][][] fscCclByType = new int[numFsc][OuType.COUNT][numCcl];

        for (int f = 0; f < numFsc; f++) {
            FSC fsc = this.instance.FSCs.get(f);

            for (Map.Entry<String, int[]> e : fsc.initialStorageLevels.entrySet()) {
                String key = e.getKey();
                int[] counts = e.getValue();

                int type = OuType.fromString(key);

                for (int c = 0; c < numCcl; c++) {
                    fscCclByType[f][type][c] = counts[c];
                }
            }
        }

        // Initial full inventory OUs
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
                0, // amount of trucks is reset in the demandStep function
                fscTrucks,
                fscCclByType,
                ouKg);

        return state.deepCopy();
    }

    public StepResult step(int actionIndex) {
        // Demand phase
        if (this.state.getPhase() == State.Phase.DEMAND) {
            return stepDemand();
        }

        // Action phase
        Action action = this.actionSpace.decode(actionIndex);
        if (!isFeasible(action)) {
            throw new IllegalStateException("The agent tries to make an illegal move: " + action +
                    " in phase=" + state.getPhase() + " day=" + state.getDay());
        }

        if (action.getType() == Action.ActionType.STOP)
            return stepStop();

        applyShipment(action);

        // No shaping on individual shipment actions; demand phase drives learning signal.
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

            for (int productIdx = 0; productIdx < this.instance.products.size(); productIdx++) {
                if (inv[ou][productIdx] < 0) {
                    stockout = true;
                    stockoutAmountKg += -inv[ou][productIdx];
                    ouStockout = true;
                    inv[ou][productIdx] = 0;
                }
            }

            if (ouStockout) {
                stockoutOuCount++;
            }
        }

        lastEpisodeTotalStockoutKg += stockoutAmountKg;

        if (stockout) {
            lastEpisodeDaysSurvived = day;
            LOG.info("[Episode " + episodeCounter + "] STOCKOUT day=" + day +
                    " ouStockouts=" + stockoutOuCount);
            // Terminal penalty scales with stockout severity, but with small coefficients.
            // This keeps "survive longer days" as the dominant objective.
            double terminalPenalty = -2.0 - 0.2 * stockoutOuCount - 0.00001 * stockoutAmountKg;
            return new StepResult(state.deepCopy(), terminalPenalty, true);
        }

        lastEpisodeDaysSurvived = day;

        if (day >= horizon) {
            LOG.info("[Episode " + episodeCounter + "] SUCCESS no stockout");
            // Bonus when the full horizon is survived.
            return new StepResult(state.deepCopy(), 5.0, true);
        }

        state.setPhase(State.Phase.FSC_TO_OU);

        int[] fscTrucks = state.getFscTrucksRemaining();
        for (int i = 0; i < fscTrucks.length; i++) {
            fscTrucks[i] = maxFscTrucksPerDay[i];
        }

        // Per-day survival reward (dominant objective).
        return new StepResult(state.deepCopy(), 2.0, false);
    }

    private StepResult stepStop() {
        // Stop at FSC_TO_OU phase
        if (state.getPhase() == State.Phase.FSC_TO_OU) {
            state.setPhase(State.Phase.MSC_TO_FSC);
            state.setMscTrucksRemaining(maxMscTrucksPerDay);
            return new StepResult(state.deepCopy(), 0.0, false);
        }

        // Stop at MSC_TO_FSC phase
        if (state.getPhase() == State.Phase.MSC_TO_FSC) {
            state.setDay(state.getDay() + 1);
            state.setPhase(State.Phase.DEMAND);
            return new StepResult(state.deepCopy(), 0.0, false);
        }

        throw new IllegalStateException("STOP in wrong phase");
    }

    /**
     * Legal action mask for actor-critic.
     */
    public boolean[] getLegalActionMask() {
        boolean[] mask = new boolean[actionSpace.size()];
        boolean anyNonStopFeasibleAcions = false;

        for (int i = 0; i < mask.length; i++) {
            Action a = actionSpace.decode(i);
            boolean feasible = isFeasible(a);
            mask[i] = feasible;

            if (feasible && a.getType() != Action.ActionType.STOP) {
                anyNonStopFeasibleAcions = true;
            }
        }

        int stopIdx = actionSpace.getStopIndex();

        // Optional constraint:
        // if enabled, force the agent to keep assigning trucks until no shipment is feasible.
        if (forceUseAllTrucksInPhase && anyNonStopFeasibleAcions) {
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

        // STOP is treated as a phase-transition action in this environment.
        // The training mask decides when STOP is exposed to the agent.
        if (a.getType() == Action.ActionType.STOP)
            return true;


        if (state.getPhase() == State.Phase.FSC_TO_OU) {

            // During FSC_TO_OU, only FSC_TO_OU actions are valid
            if (a.getType() != Action.ActionType.FSC_TO_OU)
                return false;

            // Cannot target VUST
            int ou = a.getOuId();
            if (ou == vustOuId)
                return false;

            // Each OU can only be served from its assigned parent FSC
            int fsc = parentFscOfOu[ou];
            if (fsc < 0)
                return false;

            // Truck capacity at FSC
            if (state.getFscTrucksRemaining()[fsc] <= 0)
                return false;

            int type = ouTypeOfOuId[ou];
            int cclType = a.getCclType();

            // Cannot ship ccl if not available
            if (state.getFscCclByType()[fsc][type][cclType] <= 0)
                return false;

            // OU inventory capacity must be respected
            return ouHasCapacity(ou, cclType);
        }

        if (state.getPhase() == State.Phase.MSC_TO_FSC) {

            // Truck capacity at MSC
            if (state.getMscTrucksRemaining() <= 0)
                return false;

            if (a.getType() == Action.ActionType.MSC_TO_FSC) {
                int fsc = a.getFscId();

                // FSC total storage capacity
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
                // VUST storage capacity
                return a.getOuId() == vustOuId && ouHasCapacity(vustOuId, a.getCclType());
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

        if (name.equals("VUST")) {
            return -1;
        }
        if (name.startsWith("GN"))
            return OuType.GN.ordinal();
        if (name.startsWith("AT"))
            return OuType.AT.ordinal();
        if (name.startsWith("PAINF"))
            return OuType.PAINF.ordinal();

        throw new IllegalStateException("Cannot infer OU type from operatingUnitName=" + u.operatingUnitName);
    }

    public int getHorizon() {
        return horizon;
    }

    public int getEpisodeCounter() {
        return episodeCounter;
    }

    public State getCurrentStateCopy() {
        return state.deepCopy();
    }

    public double getLastEpisodeTotalStockoutKg() {
        return lastEpisodeTotalStockoutKg;
    }

    public int getLastEpisodeDaysSurvived() {
        return lastEpisodeDaysSurvived;
    }
}
