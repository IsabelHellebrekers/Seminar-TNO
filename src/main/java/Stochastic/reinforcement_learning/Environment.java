package Stochastic.reinforcement_learning;

import Objects.CCLpackage;
import Objects.FSC;
import Objects.Instance;
import Objects.OperatingUnit;

import java.util.*;

/**
 * Simulates the supply game as a MDP with phases:
 * DEMAND -> FSC_SEND -> MSC_SEND -> DEMAND ...
 */
public final class Environment {

    private final Instance instance;
    private final ActionSpace actionSpace;
    private final DemandModel demandModel;

    private final int horizon;

    private final int maxMscTrucksPerDay;
    private final int maxFscTrucksPerDay;

    // OU -> parent FSC index (or -1 for VUST)
    private final int[] parentFscOfOu;

    // name->fscIndex for resolving OU.source
    private final Map<String, Integer> fscIndexByName;

    // VUST ouId (the only OU directly supplied by MSC)
    private final int vustOuId;

    private State state;
    private Random rng;

    public Environment(Instance instance,
                       ActionSpace actionSpace,
                       DemandModel demandModel,
                       int maxMscTrucksPerDay,
                       int maxFscTrucksPerDay) {

        this.instance = Objects.requireNonNull(instance);
        this.actionSpace = Objects.requireNonNull(actionSpace);
        this.demandModel = Objects.requireNonNull(demandModel);

        this.horizon = instance.timeHorizon;

        this.maxMscTrucksPerDay = maxMscTrucksPerDay;
        this.maxFscTrucksPerDay = maxFscTrucksPerDay;

        this.vustOuId = actionSpace.getVustOuId();

        this.fscIndexByName = new HashMap<>();
        for (int f = 0; f < instance.FSCs.size(); f++) {
            fscIndexByName.put(instance.FSCs.get(f).FSCname, f);
        }

        this.parentFscOfOu = new int[instance.operatingUnits.size()];
        for (int ou = 0; ou < instance.operatingUnits.size(); ou++) {
            if (ou == vustOuId) {
                parentFscOfOu[ou] = -1; // MSC supplied
                continue;
            }
            String src = instance.operatingUnits.get(ou).source;
            Integer idx = fscIndexByName.get(src);
            if (idx == null) {
                throw new IllegalStateException("OU " + instance.operatingUnits.get(ou).operatingUnitName +
                        " has unknown source FSC: " + src);
            }
            parentFscOfOu[ou] = idx;
        }
    }

    public State reset(long seed) {
        this.rng = new Random(seed);

        int numFsc = instance.FSCs.size();
        int numOu = instance.operatingUnits.size();
        int numCcl = instance.cclTypes.size();

        // Trucks: start at DEMAND phase, so trucks not yet allocated for the day
        int mscTrucks = 0;
        int[] fscTrucks = new int[numFsc];
        Arrays.fill(fscTrucks, 0);

        // FSC initial inventory: sum all initialStorageLevels across ouTypes (simple fungible inventory)
        int[][] fscCcl = new int[numFsc][numCcl];
        for (int f = 0; f < numFsc; f++) {
            FSC fsc = instance.FSCs.get(f);
            for (Map.Entry<String, int[]> e : fsc.initialStorageLevels.entrySet()) {
                int[] counts = e.getValue();
                for (int c = 0; c < numCcl; c++) {
                    fscCcl[f][c] += counts[c];
                }
            }
        }

        // OU initial inventory: start full at max capacity
        double[][] ouKg = new double[numOu][3];
        for (int ou = 0; ou < numOu; ou++) {
            OperatingUnit u = instance.operatingUnits.get(ou);
            ouKg[ou][0] = u.maxFoodWaterKg;
            ouKg[ou][1] = u.maxFuelKg;
            ouKg[ou][2] = u.maxAmmoKg;
        }

        this.state = new State(
                1,
                State.Phase.DEMAND,
                mscTrucks,
                fscTrucks,
                fscCcl,
                ouKg
        );
        return state.deepCopy();
    }

    public State getState() {
        return state;
    }

    /** Returns a mask of feasible actions for the current state. */
    public boolean[] actionMask(State s) {
        boolean[] mask = new boolean[actionSpace.size()];
        for (int i = 0; i < mask.length; i++) {
            mask[i] = isFeasible(s, actionSpace.decode(i));
        }
        return mask;
    }

    /** One step = either (a) apply demand (if phase=DEMAND) or (b) apply one shipment/STOP action. */
    public StepResult step(int actionIndex) {
        if (state == null) throw new IllegalStateException("Call reset(seed) before step().");
        Action action = actionSpace.decode(actionIndex);

        // DEMAND phase: ignore the provided actionIndex (trainer can pass STOP always)
        if (state.getPhase() == State.Phase.DEMAND) {
            return stepDemand();
        }

        // Allocation phases: action must be feasible
        if (!isFeasible(state, action)) {
            throw new IllegalArgumentException("Infeasible action attempted: " + action +
                    " in phase " + state.getPhase());
        }

        if (action.getType() == Action.ActionType.STOP) {
            return stepStop();
        }

        applyShipment(state, action);

        // reward is 0 on shipment micro-steps
        return new StepResult(state.deepCopy(), 0.0, false);
    }

    // ---------------- Phase logic ----------------

    private StepResult stepDemand() {
        // Apply demand for the current day
        int day = state.getDay();

        // Subtract demand OU-by-OU
        boolean stockout = false;

        for (int ouId = 0; ouId < instance.operatingUnits.size(); ouId++) {
            OperatingUnit ou = instance.operatingUnits.get(ouId);

            // TODO: Demand model class schrijven
            // DemandModel.Demand d = demandModel.sampleDemand(ou, day, rng);

            // consume inventory
            // stockout |= consumeOu(ouId, d);
        }

        if (stockout) {
            // terminal failure
            return new StepResult(state.deepCopy(), 0.0, true);
        }

        // Survived this day => reward +1
        double reward = 1.0;

        // If we just survived day=10, end episode successfully immediately
        if (day >= horizon) {
            return new StepResult(state.deepCopy(), reward, true);
        }

        // Move to FSC allocation phase for same day (post-demand planning)
        state.setPhase(State.Phase.FSC_SEND);
        // Reset FSC trucks for this day
        int[] fscTrucks = state.getFscTrucksRemaining();
        Arrays.fill(fscTrucks, maxFscTrucksPerDay);
        // MSC trucks are unused in this phase
        state.setMscTrucksRemaining(0);

        return new StepResult(state.deepCopy(), reward, false);
    }

    private StepResult stepStop() {
        State.Phase phase = state.getPhase();

        if (phase == State.Phase.FSC_SEND) {
            // switch to MSC phase, reset MSC trucks
            state.setPhase(State.Phase.MSC_SEND);
            state.setMscTrucksRemaining(maxMscTrucksPerDay);
            // FSC trucks no longer relevant; keep as-is
            return new StepResult(state.deepCopy(), 0.0, false);
        }

        if (phase == State.Phase.MSC_SEND) {
            // end of day planning -> next day demand
            state.setDay(state.getDay() + 1);
            state.setPhase(State.Phase.DEMAND);
            // clear trucks until phases reset
            state.setMscTrucksRemaining(0);
            Arrays.fill(state.getFscTrucksRemaining(), 0);
            return new StepResult(state.deepCopy(), 0.0, false);
        }

        throw new IllegalStateException("STOP encountered in unsupported phase: " + phase);
    }

    // ---------------- Feasibility & dynamics ----------------

    private boolean isFeasible(State s, Action a) {
        // STOP always feasible in allocation phases, and also allowed in DEMAND (ignored)
        if (a.getType() == Action.ActionType.STOP) {
            return true;
        }

        State.Phase phase = s.getPhase();

        // Demand phase: no shipment actions allowed (they are ignored anyway)
        if (phase == State.Phase.DEMAND) {
            return false;
        }

        // FSC->OU phase: only FSC_TO_OU allowed AND not VUST
        if (phase == State.Phase.FSC_SEND) {
            if (a.getType() != Action.ActionType.FSC_TO_OU) return false;
            if (a.getOuId() == vustOuId) return false; // VUST cannot be supplied by FSC
            return canShipFscToOu(s, a.getOuId(), a.getCclType());
        }

        // MSC phase: allow MSC_TO_FSC and MSC_TO_OU (only VUST)
        if (phase == State.Phase.MSC_SEND) {
            if (a.getType() == Action.ActionType.MSC_TO_FSC) {
                return canShipMscToFsc(s, a.getFscId(), a.getCclType());
            }
            if (a.getType() == Action.ActionType.MSC_TO_OU) {
                return (a.getOuId() == vustOuId) && canShipMscToOu(s, a.getOuId(), a.getCclType());
            }
            return false;
        }

        return false;
    }

    private boolean canShipFscToOu(State s, int ouId, int cclIdx) {
        int fscId = parentFscOfOu[ouId];
        if (fscId < 0) return false;

        // trucks available at that FSC
        if (s.getFscTrucksRemaining()[fscId] <= 0) return false;

        // FSC has the package
        if (s.getFscCcl()[fscId][cclIdx] <= 0) return false;

        // OU capacity allows adding this package contents
        return ouHasCapacityForCcl(ouId, cclIdx);
    }

    private boolean canShipMscToFsc(State s, int fscId, int cclIdx) {
        if (s.getMscTrucksRemaining() <= 0) return false;

        // FSC CCL storage capacity
        int total = 0;
        for (int c = 0; c < instance.cclTypes.size(); c++) total += s.getFscCcl()[fscId][c];
        int cap = instance.FSCs.get(fscId).maxStorageCapCcls;
        if (total + 1 > cap) return false;

        return true;
    }

    private boolean canShipMscToOu(State s, int ouId, int cclIdx) {
        if (s.getMscTrucksRemaining() <= 0) return false;
        return ouHasCapacityForCcl(ouId, cclIdx);
    }

    private boolean ouHasCapacityForCcl(int ouId, int cclIdx) {
        OperatingUnit ou = instance.operatingUnits.get(ouId);
        CCLpackage ccl = instance.cclTypes.get(cclIdx);

        double fw = state.getOuKg()[ouId][0];
        double fuel = state.getOuKg()[ouId][1];
        double ammo = state.getOuKg()[ouId][2];

        if (fw + ccl.foodWaterKg > ou.maxFoodWaterKg) return false;
        if (fuel + ccl.fuelKg > ou.maxFuelKg) return false;
        if (ammo + ccl.ammoKg > ou.maxAmmoKg) return false;

        return true;
    }

    private void applyShipment(State s, Action a) {
        switch (a.getType()) {
            case FSC_TO_OU -> applyFscToOu(s, a.getOuId(), a.getCclType());
            case MSC_TO_FSC -> applyMscToFsc(s, a.getFscId(), a.getCclType());
            case MSC_TO_OU -> applyMscToOu(s, a.getOuId(), a.getCclType());
            default -> throw new IllegalStateException("Unexpected shipment type: " + a.getType());
        }
    }

    private void applyFscToOu(State s, int ouId, int cclIdx) {
        int fscId = parentFscOfOu[ouId];
        s.getFscTrucksRemaining()[fscId]--;
        s.getFscCcl()[fscId][cclIdx]--;

        CCLpackage ccl = instance.cclTypes.get(cclIdx);
        s.getOuKg()[ouId][0] += ccl.foodWaterKg;
        s.getOuKg()[ouId][1] += ccl.fuelKg;
        s.getOuKg()[ouId][2] += ccl.ammoKg;
    }

    private void applyMscToFsc(State s, int fscId, int cclIdx) {
        s.setMscTrucksRemaining(s.getMscTrucksRemaining() - 1);
        s.getFscCcl()[fscId][cclIdx]++;
    }

    private void applyMscToOu(State s, int ouId, int cclIdx) {
        s.setMscTrucksRemaining(s.getMscTrucksRemaining() - 1);

        CCLpackage ccl = instance.cclTypes.get(cclIdx);
        s.getOuKg()[ouId][0] += ccl.foodWaterKg;
        s.getOuKg()[ouId][1] += ccl.fuelKg;
        s.getOuKg()[ouId][2] += ccl.ammoKg;
    }
    // TODO: Demand model class schrijven
    // private boolean consumeOu(int ouId, DemandModel.Demand d) {
    //     double[][] ouKg = state.getOuKg();

    //     ouKg[ouId][0] -= d.foodWaterKg();
    //     ouKg[ouId][1] -= d.fuelKg();
    //     ouKg[ouId][2] -= d.ammoKg();

    //     return (ouKg[ouId][0] < 0.0) || (ouKg[ouId][1] < 0.0) || (ouKg[ouId][2] < 0.0);
    // }
}