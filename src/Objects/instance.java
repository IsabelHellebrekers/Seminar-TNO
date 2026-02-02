package Objects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Presents an Instance for the Capacitated Resupply Problem (CRP).
 */
public class Instance {
    // List of operating units (OUs)
    public final List<OperatingUnit> operatingUnits = new ArrayList<>();

    // Map : centre -> centre attributes (capacity, source, etc)
    public final Map<String, Centre> sourceCapacities = new HashMap<>();

    // Map : CC: type -> CCL contents in kg
    public final Map<String, CCLpackage> cclContents = new HashMap<>();

    // Initial storage levels at FSCs
    // centre -> (OU-type -> [type1, type2, type3])
    public final Map<String, Map<String, int[]>> initialStorageLevels = new HashMap<>();
}
