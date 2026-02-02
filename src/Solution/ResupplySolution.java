package Solution;
import java.util.*;

/**
 * Presents a solution to the Capacitated Resupply Problem (CRP). 
 * This class is intented for post-processing. 
 */
public class ResupplySolution {
    // Objective value : minimized number of trucks
    public final double objectiveValue;

    // Number of trucks stationed at the Main Supply Centre (MSC)
    public final int trucksMSC;

    // Number of trucks stationed at each Forward Supply Centre (FSC)
    public final Map<String, Integer> trucksFSC;

    // Total number of CCL departures from the MSC per day
    public final Map<Integer, Integer> dailyMSCDepartures;

    // Total number of CCL departures from each FSC per day
    public final Map<String, Map<Integer, Integer>> dailyFSCDepartures;

    // Operating Unit inventory trajectories across the horizon
    // OU name -> day -> product -> inventory (kg)
    public final Map<String, Map<Integer, Map<String, Double>>> ouInventory;

    // Total inventory at each FSC per day (in number of CCLs)
    // FSC name -> day -> total inventory (#CCL)
    public final Map<String, Map<Integer, Double>> fscInventoryTotal;

    // Detailed FSC inventory composition per day
    // FSC name -> day -> CCL type -> OU type -> quantity (#CCL)
    public final Map<String, Map<Integer, Map<String, Map<String, Integer>>>> fscInventoryByBucket;

    // List of all shipments from the solution
    public final List<Shipment> shipments;

    public ResupplySolution(
        double objectiveValue, 
        int trucksMSC, 
        Map<String, Integer> trucksFSC, 
        Map<Integer, Integer> dailyMSCDepartures, 
        Map<String, Map<Integer, Integer>> dailyFSCDepartures,
        Map<String, Map<Integer, Map<String, Double>>> ouInventory,
        Map<String, Map<Integer, Double>> fscInventoryTotal,
        Map<String, Map<Integer, Map<String, Map<String, Integer>>>> fscInventoryByBucket,
        List<Shipment> shipments
    ) {
        this.objectiveValue = objectiveValue;
        this.trucksMSC = trucksMSC;
        this.trucksFSC = Collections.unmodifiableMap(new LinkedHashMap<>(trucksFSC));
        this.dailyMSCDepartures = Collections.unmodifiableMap(new LinkedHashMap<>(dailyMSCDepartures));

        Map<String, Map<Integer, Integer>> tmp = new LinkedHashMap<>();
        for (var e : dailyFSCDepartures.entrySet()) {
            tmp.put(e.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(e.getValue())));
        }
        this.dailyFSCDepartures = Collections.unmodifiableMap(tmp);

        this.ouInventory = deepUnmodifiable3(ouInventory);
        this.fscInventoryTotal = deepUnmodifiable2(fscInventoryTotal);
        this.fscInventoryByBucket = deepUnmodifiable4(fscInventoryByBucket);
        this.shipments = Collections.unmodifiableList(new ArrayList<>(shipments));
    }

    private static Map<String, Map<Integer, Map<String, Map<String, Integer>>>> deepUnmodifiable4(
            Map<String, Map<Integer, Map<String, Map<String, Integer>>>> in
    ) {
        Map<String, Map<Integer, Map<String, Map<String, Integer>>>> out = new LinkedHashMap<>();
        for (var e1 : in.entrySet()) {
            Map<Integer, Map<String, Map<String, Integer>>> level2 = new LinkedHashMap<>();
            for (var e2 : e1.getValue().entrySet()) {
                Map<String, Map<String, Integer>> level3 = new LinkedHashMap<>();
                for (var e3 : e2.getValue().entrySet()) {
                    level3.put(e3.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(e3.getValue())));
                }
                level2.put(e2.getKey(), Collections.unmodifiableMap(level3));
            }
            out.put(e1.getKey(), Collections.unmodifiableMap(level2));
        }
        return Collections.unmodifiableMap(out);
    }

    private static Map<String, Map<Integer, Map<String, Double>>> deepUnmodifiable3(
            Map<String, Map<Integer, Map<String, Double>>> in
    ) {
        Map<String, Map<Integer, Map<String, Double>>> out = new LinkedHashMap<>();
        for (var e1 : in.entrySet()) {
            Map<Integer, Map<String, Double>> level2 = new LinkedHashMap<>();
            for (var e2 : e1.getValue().entrySet()) {
                level2.put(e2.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(e2.getValue())));
            }
            out.put(e1.getKey(), Collections.unmodifiableMap(level2));
        }
        return Collections.unmodifiableMap(out);
    }

    private static Map<String, Map<Integer, Double>> deepUnmodifiable2(
            Map<String, Map<Integer, Double>> in
    ) {
        Map<String, Map<Integer, Double>> out = new LinkedHashMap<>();
        for (var e : in.entrySet()) {
            out.put(e.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(e.getValue())));
        }
        return Collections.unmodifiableMap(out);
    }
    
    
}
