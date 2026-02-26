package Stochastic.reinforcement_learning;

import Objects.CCLpackage;
import Objects.FSC;
import Objects.Instance;
import Objects.OperatingUnit;

import java.util.*;

/**
 * Environment for RL.
 */
public final class Environment {

    private final Instance instance;
    private final ActionSpace actionSpace;
    private final DemandModel demandModel;

    private final int horizon;

    private final int maxMscTrucksPerDay;
    private final int maxFscTrucksPerDay;

    private final int vustOuId;

    // For non-VUST OUs: parent FSC index
    private final int[] parentFscOfOu;

    private State state;
    private Random rng;

    public Environment(Instance instance,
                       ActionSpace actionSpace,
                       DemandModel demandModel,
                       int maxMscTrucksPerDay,
                       int maxFscTrucksPerDay) {

        this.instance = instance;
        this.actionSpace = actionSpace;
        this.demandModel = demandModel;

        this.horizon = instance.timeHorizon;
        this.maxMscTrucksPerDay = maxMscTrucksPerDay;
        this.maxFscTrucksPerDay = maxFscTrucksPerDay;

        this.vustOuId = actionSpace.getVustOuId();

        // Map OU -> parent FSC
        parentFscOfOu = new int[instance.operatingUnits.size()];

        Map<String, Integer> fscIndexByName = new HashMap<>();
        for (int f = 0; f < instance.FSCs.size(); f++) {
            fscIndexByName.put(instance.FSCs.get(f).FSCname, f);
        }

        for (int ou = 0; ou < instance.operatingUnits.size(); ou++) {

            if (ou == this.vustOuId) {
                this.parentFscOfOu[ou] = -1;
                continue;
            }

            String source = instance.operatingUnits.get(ou).source;
            Integer fscIdx = fscIndexByName.get(source);
            this.parentFscOfOu[ou] = fscIdx;
        }
    }

    public State reset(long seed) {

        this.rng = new Random(seed);

        int numFsc = this.instance.FSCs.size();
        int numOu = this.instance.operatingUnits.size();
        int numCcl = this.instance.cclTypes.size();

        int[][][] fscCcl = new int[numFsc][numOu][numCcl];

        // Initialize FSC inventory
        for (int f = 0; f < numFsc; f++) {
            FSC fsc = this.instance.FSCs.get(f);

            for (Map.Entry<String, int[]> e : fsc.initialStorageLevels.entrySet()) {

                String ouName = e.getKey();
                int[] counts = e.getValue();

                int ouId = findOuIdByName(ouName);

                for (int c = 0; c < numCcl; c++) {
                    fscCcl[f][ouId][c] = counts[c];
                }
            }
        }

        // Initialize OU inventory
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
                fscCcl,
                ouKg
        );

        return state.deepCopy();
    }

    public StepResult step(int actionIndex) {
        // Demand phase
        if (this.state.getPhase() == State.Phase.DEMAND) {
            return stepDemand();
        }

        // Action phase
        Action action = this.actionSpace.decode(actionIndex);

        if (!isFeasible(action))
            throw new IllegalArgumentException("Infeasible action: " + action);

        if (action.getType() == Action.ActionType.STOP)
            return stepStop();

        applyShipment(action);

        return new StepResult(this.state.deepCopy(), 0.0, false);
    }

    // ============================================================
    // DEMAND
    // ============================================================

    private StepResult stepDemand() {

        boolean stockout = false;

        int day = state.getDay();

        for (int ou = 0; ou < instance.operatingUnits.size(); ou++) {

            // TODO: Demand model class schrijven
//            DemandModel.Demand d = this.demandModel.sampleDemand(instance.operatingUnits.get(ou), day, rng);
//
//            double[][] inv = state.getOuKg();
//
//            inv[ou][0] -= d.foodWaterKg();
//            inv[ou][1] -= d.fuelKg();
//            inv[ou][2] -= d.ammoKg();
//
//            if (inv[ou][0] < 0 || inv[ou][1] < 0 || inv[ou][2] < 0)
//                stockout = true;
        }

        if (stockout)
            return new StepResult(state.deepCopy(), 0.0, true);

        double reward = 1.0;

        if (day >= horizon)
            return new StepResult(state.deepCopy(), reward, true);

        // Move to FSC phase
        state.setPhase(State.Phase.FSC_TO_OU);
        Arrays.fill(state.getFscTrucksRemaining(), maxFscTrucksPerDay);

        return new StepResult(state.deepCopy(), reward, false);
    }

    // ============================================================
    // STOP TRANSITIONS
    // ============================================================

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

    // ============================================================
    // FEASIBILITY
    // ============================================================

    private boolean isFeasible(Action a) {

        if (a.getType() == Action.ActionType.STOP)
            return true;

        if (state.getPhase() == State.Phase.FSC_TO_OU) {

            if (a.getType() != Action.ActionType.FSC_TO_OU)
                return false;

            int ou = a.getOuId();
            int fsc = parentFscOfOu[ou];

            if (ou == vustOuId)
                return false;

            if (state.getFscTrucksRemaining()[fsc] <= 0)
                return false;

            if (state.getFscCcl()[fsc][ou][a.getCclType()] <= 0)
                return false;

            return ouHasCapacity(ou, a.getCclType());
        }

        if (state.getPhase() == State.Phase.MSC_TO_FSC) {

            if (a.getType() == Action.ActionType.MSC_TO_FSC) {

                if (state.getMscTrucksRemaining() <= 0)
                    return false;

                int fsc = a.getFscId();

                int total = 0;
                for (int ou = 0; ou < state.numOu(); ou++)
                    for (int c = 0; c < state.numCclTypes(); c++)
                        total += state.getFscCcl()[fsc][ou][c];

                return total < instance.FSCs.get(fsc).maxStorageCapCcls;
            }

            if (a.getType() == Action.ActionType.MSC_TO_OU) {

                if (state.getMscTrucksRemaining() <= 0)
                    return false;

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

    // ============================================================
    // APPLY SHIPMENT
    // ============================================================

    private void applyShipment(Action a) {
        switch (a.getType()) {
            case FSC_TO_OU -> {
                int ou = a.getOuId();
                int fsc = this.parentFscOfOu[ou];
                int ccl = a.getCclType();

                this.state.getFscTrucksRemaining()[fsc]--;
                this.state.getFscCcl()[fsc][ou][ccl]--;

                addCclToOu(ou, ccl);
            }

            case MSC_TO_FSC -> {
                this.state.setMscTrucksRemaining(this.state.getMscTrucksRemaining() - 1);

                int ou = a.getOuId();
                int fsc = a.getFscId();
                int ccl = a.getCclType();

                this.state.getFscCcl()[fsc][ou][ccl]++;
            }

            case MSC_TO_OU -> {
                this.state.setMscTrucksRemaining(this.state.getMscTrucksRemaining() - 1);

                addCclToOu(this.vustOuId, a.getCclType());
            }
        }
    }

    private void addCclToOu(int ou, int cclIdx) {

        CCLpackage ccl = instance.cclTypes.get(cclIdx);

        double[][] inv = state.getOuKg();

        inv[ou][0] += ccl.foodWaterKg;
        inv[ou][1] += ccl.fuelKg;
        inv[ou][2] += ccl.ammoKg;
    }

    // ============================================================

    private int findOuIdByName(String name) {
        for (int i = 0; i < instance.operatingUnits.size(); i++) {
            if (instance.operatingUnits.get(i).operatingUnitName.equalsIgnoreCase(name))
                return i;
        }
        throw new IllegalStateException("OU not found: " + name);
    }
}