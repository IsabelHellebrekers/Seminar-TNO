package Objects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class instance {
    public final List<OperatingUnit> operatingUnits = new ArrayList<>();
    public final Map<String, Centre> sourceCapacities = new HashMap<>();
    public final Map<String, CCLpackage> cclContents = new HashMap<>();
    // centre -> (operating unit -> [type1, type2, type3])
    public final Map<String, Map<String, int[]>> initialStorageLevels = new HashMap<>();
}
