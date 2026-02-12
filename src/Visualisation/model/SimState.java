package Visualisation.model;

import Objects.FSC;
import Objects.Instance;
import Objects.OperatingUnit;
import Objects.Result;

import java.util.HashMap;
import java.util.Map;

public class SimState {
    private final Map<String, Map<String, Double>> inventoryOU = new HashMap<>();
    private final Map<String, Map<String, Double>> inventoryOUMax = new HashMap<>();
    private final Map<String, Integer> inventoryFSC = new HashMap<>();
    private final Map<String, Integer> inventoryFSCMax = new HashMap<>();
    private final Map<String, Integer> arcTrucks = new HashMap<>();

    public static SimState from(Instance inst, Result res) {
        SimState s = new SimState();
        s.resetFrom(inst, res);
        return s;
    }

    public void resetFrom(Instance inst, Result res) {
        this.inventoryOU.clear();
        this.inventoryOUMax.clear();
        this.inventoryFSC.clear();
        this.inventoryFSCMax.clear();
        this.arcTrucks.clear();

        // Inventories for FSCs
        Map<Result.SKey, Integer> sValues = (res == null) ? null : res.getSValue();
        Map<String, Integer> fscTotals = new HashMap<>();
        if (sValues != null) {
            for (Map.Entry<Result.SKey, Integer> e : sValues.entrySet()) {
                Result.SKey key = e.getKey();
                if (key.t() != 1) continue;
                fscTotals.merge(key.fsc(), e.getValue(), Integer::sum);
            }
        }

        // Inventories for OUs
        for (OperatingUnit ou : inst.operatingUnits) {
            double fw = ou.maxFoodWaterKg;
            double fuel = ou.maxFuelKg;
            double ammo = ou.maxAmmoKg;

            if (res != null) {
                Map<Result.IKey, Double> iValues = res.getIValue();
                Double fwVal = iValues.get(new Result.IKey(ou.operatingUnitName, "FW", 1));
                Double fuelVal = iValues.get(new Result.IKey(ou.operatingUnitName, "FUEL", 1));
                Double ammoVal = iValues.get(new Result.IKey(ou.operatingUnitName, "AMMO", 1));
                if (fwVal != null) fw = fwVal;
                if (fuelVal != null) fuel = fuelVal;
                if (ammoVal != null) ammo = ammoVal;
            }

            Map<String, Double> invMap = new HashMap<>();
            invMap.put("FW", fw);
            invMap.put("FUEL", fuel);
            invMap.put("AMMO", ammo);
            this.inventoryOU.put(ou.operatingUnitName, invMap);

            Map<String, Double> maxMap = new HashMap<>();
            maxMap.put("FW", ou.maxFoodWaterKg);
            maxMap.put("FUEL", ou.maxFuelKg);
            maxMap.put("AMMO", ou.maxAmmoKg);
            this.inventoryOUMax.put(ou.operatingUnitName, maxMap);
        }

        // Inventories for FSCs
        for (FSC fsc : inst.FSCs) {
            this.inventoryFSCMax.put(fsc.FSCname, fsc.maxStorageCapCcls);
            int start = fscTotals.isEmpty() ? fsc.maxStorageCapCcls : fscTotals.getOrDefault(fsc.FSCname, 0);
            this.inventoryFSC.put(fsc.FSCname, start);
        }
    }

    // HELPER METHODS
    public double getInventoryOU(String node, String product) {
        return this.inventoryOU.get(node).get(product);
    }

    public void setInventoryOU(String node, String product, double value) {
        this.inventoryOU.computeIfAbsent(node, k -> new HashMap<>()).put(product, value);

    }

    public double getInventoryOUMax(String node, String product) {
        return this.inventoryOUMax.get(node).get(product);
    }

    public int getInventoryFSC(String node) {
        return this.inventoryFSC.get(node);
    }

    public void setInventoryFSC(String node, int value) {
        this.inventoryFSC.put(node, value);
    }

    public int getInventoryFSCMax(String node) {
        return this.inventoryFSCMax.get(node);
    }

    public void addInventoryOU(String node, String product, double delta) {
        setInventoryOU(node, product, getInventoryOU(node, product) + delta);
    }

    public void addInventoryFSC(String node, int delta) {
        setInventoryFSC(node, getInventoryFSC(node) + delta);
    }

    public void highlightArc(String from, String to, int trucks) {
        this.arcTrucks.put(from + "->" + to, trucks);
    }

    public int getArcTrucks(String from, String to) {
        return this.arcTrucks.getOrDefault(from + "->" + to, 0);
    }

    public void clearArcHighlights() {
        this.arcTrucks.clear();
    }
}
