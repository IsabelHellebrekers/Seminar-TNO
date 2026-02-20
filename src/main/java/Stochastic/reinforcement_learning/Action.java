package Stochastic.reinforcement_learning;

/**
 * One decision: assign ONE truck to a legal arc,
 * or STOP to end the current allocation phase.
 */
public final class Action {

    public enum ActionType {
        FSC_TO_OU,
        MSC_TO_FSC,
        MSC_TO_OU,
        STOP
    }

    private final ActionType type;
    private final int fscId;
    private final int ouId;
    private final int cclType;

    private Action(ActionType type, int fscId, int ouId, int cclType) {
        this.type = type;
        this.fscId = fscId;
        this.ouId = ouId;
        this.cclType = cclType;
    }

    public static Action stop() {
        return new Action(ActionType.STOP, -1, -1, -1);
    }

    public static Action mscToFsc(int fscId, int cclType) {
        if (fscId < 0) throw new IllegalArgumentException("fscId must be >= 0");
        if (cclType < 0) throw new IllegalArgumentException("cclType must be >= 0");
        return new Action(ActionType.MSC_TO_FSC, fscId, -1, cclType);
    }

    public static Action mscToOu(int ouId, int cclType) {
        if (ouId < 0) throw new IllegalArgumentException("ouId must be >= 0");
        if (cclType < 0) throw new IllegalArgumentException("cclType must be >= 0");
        return new Action(ActionType.MSC_TO_OU, -1, ouId, cclType);
    }

    public static Action fscToOu(int ouId, int cclType) {
        if (ouId < 0) throw new IllegalArgumentException("ouId must be >= 0");
        if (cclType < 0) throw new IllegalArgumentException("cclType must be >= 0");
        return new Action(ActionType.FSC_TO_OU, -1, ouId, cclType);
    }

    public ActionType getType() { return type; }
    public int getFscId() { return fscId; }
    public int getOuId() { return ouId; }
    public int getCclType() { return cclType; }

    @Override
    public String toString() {
        return switch (type) {
            case STOP -> "STOP";
            case MSC_TO_FSC -> "MSC->FSC(" + fscId + "), CCL=" + cclType;
            case MSC_TO_OU -> "MSC->OU(" + ouId + "), CCL=" + cclType;
            case FSC_TO_OU -> "FSC->OU(" + ouId + "), CCL=" + cclType;
        };
    }
}