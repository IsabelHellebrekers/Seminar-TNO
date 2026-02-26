package Stochastic.reinforcement_learning;

public enum OuType {
    GN,
    AT,
    PAINF;

    public static final int COUNT = values().length;

    public static int fromString(String s) {
        if (s == null) return -1;
        String u = s.trim().toUpperCase();
        if (u.startsWith("GN")) return GN.ordinal();
        if (u.startsWith("AT")) return AT.ordinal();
        if (u.startsWith("PAINF") || u.startsWith("PAIN")) return PAINF.ordinal();
        return -1;
    }
}