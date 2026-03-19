package Visualisation.model;

import Objects.Result;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 1 delivery event: FSC supplies OUs.
 * - Highlights FSC->OU arcs.
 * - Updates non-VUST OU inventories to their t+1 values.
 * - Decreases FSC inventories by the CCLs dispatched to OUs (sum of x[w,i,c,t]).
 *
 * @author 621349it Ies Timmerarends
 * @author 612348ih Isabel Hellebrekers
 * @author 631426ls Lena Stiebing
 * @author 661267eb Eeke Bavelaar
 */
public class FSCDeliveryEvent implements Event {
    private final int time;
    private final Map<String, Integer> arcTrucks;
    private final List<String> ouNames;
    private final List<String> fscNames;
    private final Result result;

    /**
     * Construct an FSCDeliveryEvent for day t representing FSC-to-OU resupply.
     *
     * @param time      simulation day on which the delivery occurs
     * @param arcTrucks map of arc key (e.g. "FSC_1->GN_CIE_1") to truck count
     * @param ouNames   names of all operating units in the network
     * @param fscNames  names of all FSCs in the network
     * @param result    the solved result containing inventory variable values
     */
    public FSCDeliveryEvent(int time,
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
    public int time() { return this.time; }

    @Override
    public EventType type() { return EventType.FSC_DELIVERY; }

    @Override
    public String label() { return "t=" + this.time + " FSC -> OU supply"; }

    @Override
    public void apply(SimState state) {
        state.clearArcHighlights();

        // Highlight FSC->OU arcs
        for (var e : this.arcTrucks.entrySet()) {
            int trucks = e.getValue();
            if (trucks <= 0) { continue; }
            String[] parts = e.getKey().split("->");
            if (parts.length != 2) { continue; }
            state.highlightArc(parts[0], parts[1], trucks);
        }

        // Update non-VUST OU inventories to t+1 values (demand already subtracted at 10:00)
        var I = this.result.getIValue();
        int tNext = this.time + 1;
        for (String ou : this.ouNames) {
            if (ou.equals("VUST")) { continue; }
            Double fw   = I.get(new Result.IKey(ou, "FW",   tNext));
            Double fuel = I.get(new Result.IKey(ou, "FUEL", tNext));
            Double ammo = I.get(new Result.IKey(ou, "AMMO", tNext));
            if (fw   != null) { state.setInventoryOU(ou, "FW",   fw); }
            if (fuel != null) { state.setInventoryOU(ou, "FUEL", fuel); }
            if (ammo != null) { state.setInventoryOU(ou, "AMMO", ammo); }
        }

        // Decrease FSC inventories by CCLs dispatched to OUs: sum over c of x[w,i,c,t]
        Map<String, Integer> fscDecrease = new HashMap<>();
        for (var e : this.result.getXValue().entrySet()) {
            Result.XKey key = e.getKey();
            if (key.t() != this.time) { continue; }
            fscDecrease.merge(key.fsc(), e.getValue(), Integer::sum);
        }
        for (String fsc : this.fscNames) {
            int decrease = fscDecrease.getOrDefault(fsc, 0);
            if (decrease > 0) { state.addInventoryFSC(fsc, -decrease); }
        }
    }
}
