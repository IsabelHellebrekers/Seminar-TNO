package Stochastic.reinforcement_learning;

import java.util.Arrays;

public final class State {

    public enum Phase {
        DEMAND,
        FSC_TO_OU,
        MSC_TO_FSC
    }

    private int day;
    private Phase phase;

    private int mscTrucksRemaining;
    private int[] fscTrucksRemaining;

    // STRICT inventory structure
    // [fscId][ouId][cclType]
    private int[][][] fscCcl;

    // OU inventory in kg
    // [ouId][productIndex]
    private double[][] ouKg;

    public State(int day,
                 Phase phase,
                 int mscTrucksRemaining,
                 int[] fscTrucksRemaining,
                 int[][][] fscCcl,
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
    public void setMscTrucksRemaining(int mscTrucksRemaining) {
        this.mscTrucksRemaining = mscTrucksRemaining;
    }

    public int[] getFscTrucksRemaining() { return fscTrucksRemaining; }
    public int[][][] getFscCcl() { return fscCcl; }
    public double[][] getOuKg() { return ouKg; }

    public int numFsc() { return fscCcl.length; }
    public int numOu() { return fscCcl[0].length; }
    public int numCclTypes() { return fscCcl[0][0].length; }

    // Observation encoding
    public float[] toObservationVector() {

        int len =
                1 + 3 +
                        1 + fscTrucksRemaining.length +
                        (numFsc() * numOu() * numCclTypes()) +
                        (numOu() * 3);

        float[] obs = new float[len];
        int k = 0;

        obs[k++] = day;

        obs[k++] = (phase == Phase.DEMAND) ? 1f : 0f;
        obs[k++] = (phase == Phase.FSC_TO_OU) ? 1f : 0f;
        obs[k++] = (phase == Phase.MSC_TO_FSC) ? 1f : 0f;

        obs[k++] = mscTrucksRemaining;

        for (int v : fscTrucksRemaining)
            obs[k++] = v;

        // 3D FSC inventory
        for (int f = 0; f < numFsc(); f++) {
            for (int ou = 0; ou < numOu(); ou++) {
                for (int c = 0; c < numCclTypes(); c++) {
                    obs[k++] = fscCcl[f][ou][c];
                }
            }
        }

        // OU kg inventory
        for (int ou = 0; ou < numOu(); ou++) {
            obs[k++] = (float) ouKg[ou][0];
            obs[k++] = (float) ouKg[ou][1];
            obs[k++] = (float) ouKg[ou][2];
        }

        return obs;
    }

    public State deepCopy() {

        int[] fscTrucksCopy =
                Arrays.copyOf(fscTrucksRemaining, fscTrucksRemaining.length);

        int[][][] fscCclCopy =
                new int[fscCcl.length][][];

        for (int f = 0; f < fscCcl.length; f++) {
            fscCclCopy[f] = new int[fscCcl[f].length][];
            for (int ou = 0; ou < fscCcl[f].length; ou++) {
                fscCclCopy[f][ou] =
                        Arrays.copyOf(fscCcl[f][ou], fscCcl[f][ou].length);
            }
        }

        double[][] ouKgCopy =
                new double[ouKg.length][];

        for (int ou = 0; ou < ouKg.length; ou++) {
            ouKgCopy[ou] =
                    Arrays.copyOf(ouKg[ou], ouKg[ou].length);
        }

        return new State(day, phase,
                mscTrucksRemaining,
                fscTrucksCopy,
                fscCclCopy,
                ouKgCopy);
    }
}