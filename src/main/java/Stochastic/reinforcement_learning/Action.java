package Stochastic.reinforcement_learning;

/**
 * One decision: assign ONE truck to a legal arc,
 * or STOP to end the current allocation phase.
 *
 * Important: When MSC sends to an FSC, the CCL is pre-dedicated to an OU TYPE (GN/AT/PAINF).
 * Therefore MSC->FSC actions must include an ouTypeBucket.
 */
public final class Action {

    public enum ActionType {
        FSC_TO_OU,   // destination: specific OU (implies OU type via OU name)
        MSC_TO_FSC,  // destination: FSC + OU type bucket
        MSC_TO_OU,   // destination: VUST
        STOP
    }

    private final ActionType type;
    private final int fscId;      // only for MSC_TO_FSC
    private final int ouId;       // for FSC_TO_OU and MSC_TO_OU
    private final int ouType;     // for MSC_TO_FSC (0..OuType.COUNT-1)
    private final int cclType;

    private Action(ActionType type, int fscId, int ouId, int ouType, int cclType) {
        this.type = type;
        this.fscId = fscId;
        this.ouId = ouId;
        this.ouType = ouType;
        this.cclType = cclType;
    }

    public static Action stop() {
        return new Action(ActionType.STOP, -1, -1, -1, -1);
    }

    public static Action mscToFsc(int fscId, int ouType, int cclType) {
        if (fscId < 0) throw new IllegalArgumentException("fscId must be >= 0");
        if (ouType < 0 || ouType >= OuType.COUNT) throw new IllegalArgumentException("ouType must be in [0," + (OuType.COUNT-1) + "]");
        if (cclType < 0) throw new IllegalArgumentException("cclType must be >= 0");
        return new Action(ActionType.MSC_TO_FSC, fscId, -1, ouType, cclType);
    }

    public static Action mscToOu(int ouId, int cclType) {
        if (ouId < 0) throw new IllegalArgumentException("ouId must be >= 0");
        if (cclType < 0) throw new IllegalArgumentException("cclType must be >= 0");
        return new Action(ActionType.MSC_TO_OU, -1, ouId, -1, cclType);
    }

    public static Action fscToOu(int ouId, int cclType) {
        if (ouId < 0) throw new IllegalArgumentException("ouId must be >= 0");
        if (cclType < 0) throw new IllegalArgumentException("cclType must be >= 0");
        return new Action(ActionType.FSC_TO_OU, -1, ouId, -1, cclType);
    }

    public ActionType getType() { return type; }
    public int getFscId() { return fscId; }
    public int getOuId() { return ouId; }
    public int getOuType() { return ouType; }
    public int getCclType() { return cclType; }

    @Override
    public String toString() {
        return switch (type) {
            case STOP -> "STOP";
            case MSC_TO_FSC -> "MSC->FSC(" + fscId + "), OUTYPE=" + ouType + ", CCL=" + cclType;
            case MSC_TO_OU -> "MSC->OU(" + ouId + "), CCL=" + cclType;
            case FSC_TO_OU -> "FSC->OU(" + ouId + "), CCL=" + cclType;
        };
    }
}