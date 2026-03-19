package Visualisation.model;

import Objects.Result;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeliveryEvent implements Event {
    private final int time;
    private final Map<String, Integer> arcTrucks;
    private final List<String> ouNames;
    private final List<String> fscNames;
    private final Result result;

    public DeliveryEvent(int time,
                         Map<String, Integer> arcTrucks,
                         List<String> ouNames,
                         List<String> fscNames,
                         Result result) {
        this.time = time;
        this.arcTrucks = Collections.unmodifiableMap(new HashMap<>(arcTrucks));
        this.ouNames = List.copyOf(ouNames);
        this.fscNames = List.copyOf(fscNames);
        this.result = result;
    }


    @Override
    public int time() {
        return this.time;
    }

    @Override
    public EventType type() {
        return EventType.DELIVERY;
    }

    @Override
    public String label() {
        return "t=" + this.time + " MSC -> FSC/VUST supply";
    }

    @Override
    public void apply(SimState state) {
        state.clearArcHighlights();

        // Highlight MSC->FSC and MSC->VUST arcs only
        for (var e : this.arcTrucks.entrySet()) {
            int trucks = e.getValue();
            if (trucks <= 0) { continue; }
            String[] parts = e.getKey().split("->");
            if (parts.length != 2) { continue; }
            state.highlightArc(parts[0], parts[1], trucks);
        }

        // Update VUST inventory to t+1 value (VUST is supplied directly by MSC)
        var I = this.result.getIValue();
        int tNext = this.time + 1;
        Double fw   = I.get(new Result.IKey("VUST", "FW",   tNext));
        Double fuel = I.get(new Result.IKey("VUST", "FUEL", tNext));
        Double ammo = I.get(new Result.IKey("VUST", "AMMO", tNext));
        if (fw   != null) { state.setInventoryOU("VUST", "FW",   fw); }
        if (fuel != null) { state.setInventoryOU("VUST", "FUEL", fuel); }
        if (ammo != null) { state.setInventoryOU("VUST", "AMMO", ammo); }

        // Bring FSC inventories up to S[w, t+1] totals after MSC replenishment
        Map<String, Integer> totals = new HashMap<>();
        for (var e : this.result.getSValue().entrySet()) {
            var key = e.getKey();
            if (key.t() != tNext) { continue; }
            totals.merge(key.fsc(), e.getValue(), Integer::sum);
        }
        for (String fsc : this.fscNames) {
            if (totals.containsKey(fsc)) {
                state.setInventoryFSC(fsc, totals.get(fsc));
            }
        }
    }

}
