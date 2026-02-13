package Visualisation.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DemandEvent implements Event {
    private final int time;
    private final Map<String, Demand> demands;

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
            state.addInventoryOU(ou, "FW", -d.fw);
            state.addInventoryOU(ou, "FUEL", -d.fuel);
            state.addInventoryOU(ou, "AMMO", -d.ammo);
        }
    }

    public static class Demand {
        public final double fw;
        public final double fuel;
        public final double ammo;

        public Demand(double fw, double fuel, double ammo) {
            this.fw = fw;
            this.fuel = fuel;
            this.ammo = ammo;
        }
    }
}
