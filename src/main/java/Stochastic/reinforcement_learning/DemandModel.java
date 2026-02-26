package Stochastic.reinforcement_learning;

import Objects.OperatingUnit;

import java.util.Random;

/**
 * Demand sampling model.
 *
 * Distributions:
 * - Food/water: Uniform(0.8, 1.2) * daily usage
 * - Fuel: Binomial(n=25, p=0.4) * daily usage / 10
 * - Ammo: Triangular(min=0.2, mode=0.8, max=2.0) * daily usage
 */
public final class DemandModel {

    public record Demand(double foodWaterKg, double fuelKg, double ammoKg) {}

    private static final double MIN_UNI = 0.8;
    private static final double MAX_UNI = 1.2;

    private static final int BINOM_N = 25;
    private static final double BINOM_P = 0.4;

    private static final double TRI_MIN = 0.2;
    private static final double TRI_MODE = 0.8;
    private static final double TRI_MAX = 2.0;

    public Demand sampleDemand(OperatingUnit ou, int day, Random rng) {
        double dailyFW = ou.dailyFoodWaterKg;
        double dailyFuel = ou.dailyFuelKg;
        double dailyAmmo = ou.dailyAmmoKg;

        double fw = uniform(rng, MIN_UNI, MAX_UNI) * dailyFW;
        double fuel = binomial(rng, BINOM_N, BINOM_P) * dailyFuel / 10.0;
        double ammo = triangular(rng, TRI_MIN, TRI_MODE, TRI_MAX) * dailyAmmo;

        return new Demand(fw, fuel, ammo);
    }

    private static double uniform(Random rng, double min, double max) {
        return min + rng.nextDouble() * (max - min);
    }

    private static int binomial(Random rng, int n, double p) {
        int successes = 0;
        for (int i = 0; i < n; i++) {
            if (rng.nextDouble() < p) successes++;
        }
        return successes;
    }

    /**
     * Triangular(min, mode, max) using inverse CDF.
     * Requires min <= mode <= max.
     */
    private static double triangular(Random rng, double min, double mode, double max) {
        double u = rng.nextDouble();
        double c = (mode - min) / (max - min);

        if (u < c) {
            return min + Math.sqrt(u * (max - min) * (mode - min));
        } else {
            return max - Math.sqrt((1.0 - u) * (max - min) * (max - mode));
        }
    }
}