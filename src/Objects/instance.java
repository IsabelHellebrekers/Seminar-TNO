package Objects;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class instance {
    public final List<OperatingUnit> operatingUnits = new ArrayList<>();
    public final Map<String, Centre> sourceCapacities = new LinkedHashMap<>();
    public final Map<String, CCLpackage> cclContents = new LinkedHashMap<>();
}