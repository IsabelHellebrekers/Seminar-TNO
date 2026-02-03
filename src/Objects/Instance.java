package Objects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Presents an Instance for the Capacitated Resupply Problem (CRP).
 */
public class Instance {
    public final List<OperatingUnit> operatingUnits = new ArrayList<>();
    public final List<FSC> FSCs = new ArrayList<>();
    public final Map<String, CCLpackage> cclContents = new HashMap<>();
}
