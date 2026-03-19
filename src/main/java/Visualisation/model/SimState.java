package Visualisation.model;

import Objects.FSC;
import Objects.Instance;
import Objects.OperatingUnit;
import Objects.Result;

import java.util.HashMap;
import java.util.Map;

/**
 * Mutable simulation state for the supply network visualiser.
 * Holds current OU inventories, FSC CCL counts, and arc truck highlights.
 * Updated by each {@link Event} as the simulation progresses.
 *
 * @author 621349it Ies Timmerarends
 * @author 612348ih Isabel Hellebrekers
 * @author 631426ls Lena Stiebing
 * @author 661267eb Eeke Bavelaar
 */
public class SimState {
    private final Map<String, Map<String, Double>> inventoryOU = new HashMap<>();
    private final Map<String, Map<String, Double>> inventoryOUMax = new HashMap<>();
    private final Map<String, Integer> inventoryFSC = new HashMap<>();
    private final Map<String, Integer> inventoryFSCMax = new HashMap<>();
    private final Map<String, Integer> arcTrucks = new HashMap<>();

    /**
     * Create a SimState initialised from the given instance and result.
     *
     * @param inst the problem instance defining network structure and capacities
     * @param res  the solved result used to set initial inventory values, or null for defaults
     * @return a new SimState ready for simulation
     */
    public static SimState from(Instance inst, Result res) {
        SimState s = new SimState();
        s.resetFrom(inst, res);
        return s;
    }

    /**
     * Reset this state to initial values derived from the given instance and result.
     *
     * @param inst the problem instance defining network structure and capacities
     * @param res  the solved result used to set initial inventory values, or null for defaults
     */
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
                if (key.t() != 1) { continue; }
                fscTotals.merge(key.fsc(), e.getValue(), Integer::sum);
            }
        }

        // Inventories for OUs
        for (OperatingUnit ou : inst.getOperatingUnits()) {
            double fw = ou.getMaxFoodWaterKg();
            double fuel = ou.getMaxFuelKg();
            double ammo = ou.getMaxAmmoKg();

            if (res != null) {
                Map<Result.IKey, Double> iValues = res.getIValue();
                Double fwVal = iValues.get(new Result.IKey(ou.getName(), "FW", 1));
                Double fuelVal = iValues.get(new Result.IKey(ou.getName(), "FUEL", 1));
                Double ammoVal = iValues.get(new Result.IKey(ou.getName(), "AMMO", 1));
                if (fwVal != null) {
                    fw = fwVal;
                }
                if (fuelVal != null) {
                    fuel = fuelVal;
                }
                if (ammoVal != null) {
                    ammo = ammoVal;
                }
            }

            Map<String, Double> invMap = new HashMap<>();
            invMap.put("FW", fw);
            invMap.put("FUEL", fuel);
            invMap.put("AMMO", ammo);
            this.inventoryOU.put(ou.getName(), invMap);

            Map<String, Double> maxMap = new HashMap<>();
            maxMap.put("FW", ou.getMaxFoodWaterKg());
            maxMap.put("FUEL", ou.getMaxFuelKg());
            maxMap.put("AMMO", ou.getMaxAmmoKg());
            this.inventoryOUMax.put(ou.getName(), maxMap);
        }

        // Inventories for FSCs
        for (FSC fsc : inst.getFSCs()) {
            this.inventoryFSCMax.put(fsc.getName(), fsc.getMaxStorageCapCcls());
            int start = fscTotals.isEmpty() ? fsc.getMaxStorageCapCcls() : fscTotals.getOrDefault(fsc.getName(), 0);
            this.inventoryFSC.put(fsc.getName(), start);
        }
    }

    // HELPER METHODS

    /**
     * Returns the current inventory level of a product at an OU.
     *
     * @param node    the OU name
     * @param product the product name (FW, FUEL, or AMMO)
     * @return current inventory level in kg
     */
    public double getInventoryOU(String node, String product) {
        return this.inventoryOU.get(node).get(product);
    }

    /**
     * Set the inventory level of a product at an OU.
     *
     * @param node    the OU name
     * @param product the product name (FW, FUEL, or AMMO)
     * @param value   the new inventory level in kg
     */
    public void setInventoryOU(String node, String product, double value) {
        this.inventoryOU.computeIfAbsent(node, k -> new HashMap<>()).put(product, value);

    }

    /**
     * Returns the maximum storage capacity of a product at an OU.
     *
     * @param node    the OU name
     * @param product the product name (FW, FUEL, or AMMO)
     * @return maximum inventory level in kg
     */
    public double getInventoryOUMax(String node, String product) {
        return this.inventoryOUMax.get(node).get(product);
    }

    /**
     * Returns the current total CCL inventory at an FSC.
     *
     * @param node the FSC name
     * @return current CCL count
     */
    public int getInventoryFSC(String node) {
        return this.inventoryFSC.get(node);
    }

    /**
     * Set the total CCL inventory at an FSC.
     *
     * @param node  the FSC name
     * @param value the new CCL count
     */
    public void setInventoryFSC(String node, int value) {
        this.inventoryFSC.put(node, value);
    }

    /**
     * Returns the maximum CCL storage capacity of an FSC.
     *
     * @param node the FSC name
     * @return maximum CCL count
     */
    public int getInventoryFSCMax(String node) {
        return this.inventoryFSCMax.get(node);
    }

    /**
     * Add a delta to the inventory level of a product at an OU.
     *
     * @param node    the OU name
     * @param product the product name (FW, FUEL, or AMMO)
     * @param delta   the amount to add (use negative values for consumption)
     */
    public void addInventoryOU(String node, String product, double delta) {
        setInventoryOU(node, product, getInventoryOU(node, product) + delta);
    }

    /**
     * Add a delta to the CCL inventory at an FSC.
     *
     * @param node  the FSC name
     * @param delta the amount to add (use negative values for dispatch)
     */
    public void addInventoryFSC(String node, int delta) {
        setInventoryFSC(node, getInventoryFSC(node) + delta);
    }

    /**
     * Record a truck count for an arc to be highlighted in the visualiser.
     *
     * @param from   the source node name
     * @param to     the destination node name
     * @param trucks the number of trucks on this arc
     */
    public void highlightArc(String from, String to, int trucks) {
        this.arcTrucks.put(from + "->" + to, trucks);
    }

    /**
     * Returns the highlighted truck count for an arc.
     *
     * @param from the source node name
     * @param to   the destination node name
     * @return the number of trucks on this arc, or 0 if not highlighted
     */
    public int getArcTrucks(String from, String to) {
        return this.arcTrucks.getOrDefault(from + "->" + to, 0);
    }

    /**
     * Clear all arc highlight data (reset truck counts to zero for all arcs).
     */
    public void clearArcHighlights() {
        this.arcTrucks.clear();
    }
}
