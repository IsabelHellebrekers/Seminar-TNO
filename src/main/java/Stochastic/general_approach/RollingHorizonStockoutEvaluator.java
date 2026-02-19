package Stochastic.general_approach;

import Objects.Instance;
import Objects.OperatingUnit;
import Stochastic.Sampling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Evaluates an instance across a rolling-horizon where demand is realized every day.
 * Per day:
 *  1) Draw random demand per OU
 *  2) Check if it causes any stockout (any product goes below 0)
 *     - if stockout: record StockOut (aggregated per OU source FSC for that day)
 *     - else: reduce demand from inventory
 *  3) Call MILP to resupply and return a new instance with updated inventories & horizon
 *  4) Update time horizon
 */
public class RollingHorizonStockoutEvaluator {
    private final long seed;
    private final OnePeriodMILP onePeriodMILP;
    private final List<StockOut> stockoutList;

    public RollingHorizonStockoutEvaluator(long seed, OnePeriodMILP onePeriodMILP) {
        this.seed = seed;
        this.onePeriodMILP = onePeriodMILP;
        this.stockoutList = new ArrayList<>();
    }

    public List<StockOut> getStockoutList() {
        return Collections.unmodifiableList(this.stockoutList);
    }

    /**
     * Runs a full rolling-horizon simulation until timeHorizon == 0.
     */
    public List<StockOut> evaluate(Instance instance) {
        //TODO: Hier misschien een deepcopy maken zodat we de originele instance wel houden?
        Sampling sampler = new Sampling(this.seed);

        int day = 1;
        //TODO: klopt dit met > 0? Of moet het >= 0 zijn?
        while (instance.timeHorizon > 0) {
            for (OperatingUnit ou : instance.operatingUnits) {

                // 1) Draw daily demand realization
                double realisedDemandFW   = sampler.uniform() * ou.dailyFoodWaterKg;
                double realisedDemandFuel = sampler.binomial() * ou.dailyFuelKg;
                double realisedDemandAmmo = sampler.triangular() * ou.dailyAmmoKg;

                // 2) Check stockout + apply if feasible
                double deficit = 0.0;
                deficit += applyDemandAndGetDeficitFW(ou, realisedDemandFW);
                deficit += applyDemandAndGetDeficitFuel(ou, realisedDemandFuel);
                deficit += applyDemandAndGetDeficitAmmo(ou, realisedDemandAmmo);

                if (deficit > 0.0) {
                    // Aggregate stockout event per FSC source and day
                    this.stockoutList.add(new StockOut(ou.operatingUnitName, day, deficit));
                }
            }

            // 3) Resupply and obtain a new instance
            instance = this.onePeriodMILP.resupply(instance);

            // 4) Update time horizon
            instance.updateTimeHorizon();
            day++;
        }
        return getStockoutList();
    }

    private static double applyDemandAndGetDeficitFW(OperatingUnit ou, double demandKg) {
        double before = ou.storageLevelFWKg;
        if (before >= demandKg) {
            ou.storageLevelFWKg = before - demandKg;
            return 0.0;
        }
        ou.storageLevelFWKg = 0.0;
        return demandKg - before;
    }

    private static double applyDemandAndGetDeficitFuel(OperatingUnit ou, double demandKg) {
        if (demandKg <= 0.0) return 0.0;
        double before = ou.storageLevelFuelKg;
        if (before >= demandKg) {
            ou.storageLevelFuelKg = before - demandKg;
            return 0.0;
        }
        ou.storageLevelFuelKg = 0.0;
        return demandKg - before;
    }

    private static double applyDemandAndGetDeficitAmmo(OperatingUnit ou, double demandKg) {
        if (demandKg <= 0.0) return 0.0;
        double before = ou.storageLevelAmmoKg;
        if (before >= demandKg) {
            ou.storageLevelAmmoKg = before - demandKg;
            return 0.0;
        }
        ou.storageLevelAmmoKg = 0.0;
        return demandKg - before;
    }
}
