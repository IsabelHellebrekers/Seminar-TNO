package Visualisation.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Simulation event representing daily demand consumption at all OUs.
 * Deducts each OU's demand from its current inventory.
 *
 * @author 621349it Ies Timmerarends
 * @author 612348ih Isabel Hellebrekers
 * @author 631426ls Lena Stiebing
 * @author 661267eb Eeke Bavelaar
 */
public class DemandEvent implements Event {
    private final int time;
    private final Map<String, Demand> demands;

    /**
     * Construct a DemandEvent for the given day.
     *
     * @param time    simulation day on which demand is consumed
     * @param demands map of OU name to its demand triple for this day
     */
    public DemandEvent(int time, Map<String, Demand> demands) {
        this.time = time;
        this.demands = Collections.unmodifiableMap(new HashMap<>(demands));
    }

    @Override
    public int time() {
            return this.time;
    }

    @Override
    public EventType type() {
        return EventType.DEMAND;
    }

    @Override
    public String label() {
        return "t=" + this.time + " Demand (all OUs)";
    }

    @Override
    public void apply(SimState state) {
        for (var e : this.demands.entrySet()) {
            String ou = e.getKey();
            Demand d = e.getValue();
            state.addInventoryOU(ou, "FW", -d.getFw());
            state.addInventoryOU(ou, "FUEL", -d.getFuel());
            state.addInventoryOU(ou, "AMMO", -d.getAmmo());
        }
    }

    /**
     * Demand triple for a single OU on a single day.
     */
    public static class Demand {
        private final double fw;
        private final double fuel;
        private final double ammo;

        /**
         * Construct a Demand triple.
         *
         * @param fw   food/water demand (kg)
         * @param fuel fuel demand (kg)
         * @param ammo ammunition demand (kg)
         */
        public Demand(double fw, double fuel, double ammo) {
            this.fw = fw;
            this.fuel = fuel;
            this.ammo = ammo;
        }

        /**
         * Returns the food/water demand.
         *
         * @return food/water demand (kg)
         */
        public double getFw() { return fw; }

        /**
         * Returns the fuel demand.
         *
         * @return fuel demand (kg)
         */
        public double getFuel() { return fuel; }

        /**
         * Returns the ammunition demand.
         *
         * @return ammunition demand (kg)
         */
        public double getAmmo() { return ammo; }
    }
}
