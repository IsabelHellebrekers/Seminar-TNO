package Stochastic.reinforcement_learning;

import java.util.Arrays;

/**
 * The state of the environment
 * Inventories:
 *  - FSC inventory stored as counts of CCLs per type: fscCcl[fscId][cclTypeIdx]
 *  - OU inventory stored as kg per product: ouKg[ouId][productIdx] (0=FW,1=FUEL,2=AMMO)
 */
public final class State {

    public enum Phase {
        DEMAND,
        FSC_SEND,
        MSC_SEND
    }

    private int day;
    private Phase phase;

    private int mscTrucksRemaining;
    private int[] fscTrucksRemaining;

    private int[][] fscCcl;
    private double[][] ouKg;

    public State(int day,
                 Phase phase,
                 int mscTrucksRemaining,
                 int[] fscTrucksRemaining,
                 int[][] fscCcl,
                 double[][] ouKg) {
        this.day = day;
        this.phase = phase;
        this.mscTrucksRemaining = mscTrucksRemaining;
        this.fscTrucksRemaining = fscTrucksRemaining;
        this.fscCcl = fscCcl;
        this.ouKg = ouKg;
    }

    public int getDay() { return day; }
    public void setDay(int day) { this.day = day; }

    public Phase getPhase() { return phase; }
    public void setPhase(Phase phase) { this.phase = phase; }

    public int getMscTrucksRemaining() { return mscTrucksRemaining; }
    public void setMscTrucksRemaining(int mscTrucksRemaining) { this.mscTrucksRemaining = mscTrucksRemaining; }

    public int[] getFscTrucksRemaining() { return fscTrucksRemaining; }
    public void setFscTrucksRemaining(int[] fscTrucksRemaining) { this.fscTrucksRemaining = fscTrucksRemaining; }

    public int[][] getFscCcl() { return fscCcl; }
    public double[][] getOuKg() { return ouKg; }

    public int numFsc() { return fscCcl.length; }
    public int numOu() { return ouKg.length; }
    public int numCclTypes() { return fscCcl[0].length; }

    /** Observation vector for NN. */
    public float[] toObservationVector() {
        int phases = 3;
        int len =
                1                               // Day
                + phases                        // # phases
                + 1                             // MSC trucks remaining
                + fscTrucksRemaining.length     // FSC trucks remaining
                + (numFsc() * numCclTypes())    // FSC inventory possibilities
                + (numOu() * 3);                // OU inventory possibilities

        float[] obs = new float[len];
        int k = 0;

        obs[k++] = day;

        obs[k++] = (phase == Phase.DEMAND) ? 1f : 0f;
        obs[k++] = (phase == Phase.FSC_SEND) ? 1f : 0f;
        obs[k++] = (phase == Phase.MSC_SEND) ? 1f : 0f;

        obs[k++] = mscTrucksRemaining;

        for (int v : fscTrucksRemaining) obs[k++] = v;

        for (int f = 0; f < numFsc(); f++) {
            for (int c = 0; c < numCclTypes(); c++) {
                obs[k++] = fscCcl[f][c];
            }
        }

        for (int ou = 0; ou < numOu(); ou++) {
            obs[k++] = (float) ouKg[ou][0]; // FW
            obs[k++] = (float) ouKg[ou][1]; // FUEL
            obs[k++] = (float) ouKg[ou][2]; // AMMO
        }

        return obs;
    }

    public State deepCopy() {
        int[] fscTrucksCopy = Arrays.copyOf(fscTrucksRemaining, fscTrucksRemaining.length);

        int[][] fscCclCopy = new int[fscCcl.length][];
        for (int i = 0; i < fscCcl.length; i++) {
            fscCclCopy[i] = Arrays.copyOf(fscCcl[i], fscCcl[i].length);
        }

        double[][] ouKgCopy = new double[ouKg.length][];
        for (int i = 0; i < ouKg.length; i++) {
            ouKgCopy[i] = Arrays.copyOf(ouKg[i], ouKg[i].length);
        }

        return new State(day, phase, mscTrucksRemaining, fscTrucksCopy, fscCclCopy, ouKgCopy);
    }
}