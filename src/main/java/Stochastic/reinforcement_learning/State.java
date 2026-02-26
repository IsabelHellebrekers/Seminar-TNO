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

    // [fscId][ouType][cclType]
    private int[][][] fscCclByType;

    // [ouId][productIndex]
    private double[][] ouKg;

    public State(int day,
                 Phase phase,
                 int mscTrucksRemaining,
                 int[] fscTrucksRemaining,
                 int[][][] fscCclByType,
                 double[][] ouKg) {

        this.day = day;
        this.phase = phase;
        this.mscTrucksRemaining = mscTrucksRemaining;
        this.fscTrucksRemaining = fscTrucksRemaining;
        this.fscCclByType = fscCclByType;
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

    public int[][][] getFscCclByType() { return fscCclByType; }
    public double[][] getOuKg() { return ouKg; }

    public int numFsc() { return fscCclByType.length; }
    public int numOuTypes() { return fscCclByType[0].length; }
    public int numCclTypes() { return fscCclByType[0][0].length; }
    public int numOu() { return ouKg.length; }

    // Observation encoding
    public float[] toObservationVector(int horizonDays) {

        int remainingDays = Math.max(0, horizonDays - day);

        int len =
                1 + 1 + 3 + // day, remainingDays, phase one-hot
                        1 + fscTrucksRemaining.length + // msc trucks + each fsc trucks
                        (numFsc() * numOuTypes() * numCclTypes()) +
                        (numOu() * 3);

        float[] obs = new float[len];
        int k = 0;

        obs[k++] = day;
        obs[k++] = remainingDays;

        obs[k++] = (phase == Phase.DEMAND) ? 1f : 0f;
        obs[k++] = (phase == Phase.FSC_TO_OU) ? 1f : 0f;
        obs[k++] = (phase == Phase.MSC_TO_FSC) ? 1f : 0f;

        obs[k++] = mscTrucksRemaining;

        for (int v : fscTrucksRemaining)
            obs[k++] = v;

        for (int f = 0; f < numFsc(); f++) {
            for (int t = 0; t < numOuTypes(); t++) {
                for (int c = 0; c < numCclTypes(); c++) {
                    obs[k++] = fscCclByType[f][t][c];
                }
            }
        }

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

        int[][][] fscCopy = new int[fscCclByType.length][][];
        for (int f = 0; f < fscCclByType.length; f++) {
            fscCopy[f] = new int[fscCclByType[f].length][];
            for (int t = 0; t < fscCclByType[f].length; t++) {
                fscCopy[f][t] = Arrays.copyOf(fscCclByType[f][t], fscCclByType[f][t].length);
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
                fscCopy,
                ouKgCopy);
    }
}