package Stochastic.reinforcement_learning;

import Objects.Instance;
import Objects.OperatingUnit;

import java.util.*;

public final class ActionSpace {

    private final List<Action> actions = new ArrayList<>();
    private final Map<ActionKey, Integer> indexByKey = new HashMap<>();

    private final int stopIndex;

    private final int numFsc;
    private final int numOu;
    private final int numCclTypes;

    private final int vustOuId;

    public ActionSpace(Instance instance) {
        Objects.requireNonNull(instance);

        this.numFsc = instance.getFSCs().size();
        this.numOu = instance.getOperatingUnits().size();
        this.numCclTypes = instance.getCCLtypes().size();

        this.vustOuId = findVustOuId(instance);

        // FSC->OU actions
        for (int ouId = 0; ouId < numOu; ouId++) {
            for (int c = 0; c < numCclTypes; c++) {
                add(Action.fscToOu(ouId, c));
            }
        }

        // MSC->FSC actions
        for (int fscId = 0; fscId < numFsc; fscId++) {
            for (int c = 0; c < numCclTypes; c++) {
                add(Action.mscToFsc(fscId, c));
            }
        }

        // MSC->VUST actions
        for (int c = 0; c < numCclTypes; c++) {
            add(Action.mscToOu(vustOuId, c));
        }

        // STOP action
        this.stopIndex = add(Action.stop());
    }

    private int findVustOuId(Instance instance) {
        List<OperatingUnit> ous = instance.getOperatingUnits();
        for (int i = 0; i < ous.size(); i++) {
            OperatingUnit ou = ous.get(i);
            if (ou.operatingUnitName.equals("VUST")) {
                return i;
            }
        }
        throw new IllegalStateException("Could not find OU with name VUST in instance.");
    }

    private int add(Action action) {
        ActionKey key = ActionKey.from(action);

        int idx = actions.size();
        actions.add(action);
        indexByKey.put(key, idx);
        return idx;
    }

    public int size() {
        return actions.size();
    }

    public Action decode(int actionIndex) {
        return actions.get(actionIndex);
    }

    public int encode(Action action) {
        Integer idx = indexByKey.get(ActionKey.from(action));
        if (idx == null)
            throw new IllegalArgumentException("Action not in ActionSpace: " + action);
        return idx;
    }

    public int getStopIndex() {
        return stopIndex;
    }

    public int getNumFsc() {
        return numFsc;
    }

    public int getNumOu() {
        return numOu;
    }

    public int getNumCclTypes() {
        return numCclTypes;
    }

    public int getVustOuId() {
        return vustOuId;
    }

    private record ActionKey(Action.ActionType type, int fscId, int ouId, int cclType) {
        static ActionKey from(Action a) {
            return new ActionKey(a.getType(), a.getFscId(), a.getOuId(), a.getCclType());
        }
    }
}