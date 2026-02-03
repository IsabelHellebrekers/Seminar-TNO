package Objects;

import java.util.List;
import java.util.Map;

public class FSC {
    public final String FSCname;
    public final Integer maxStorageCapCcls;
    public final List<OperatingUnit> servedOUs;
    public final Map<String, Map<String, int[]>> initialStorageLevels;


    public FSC(String FSCname, Integer maxStorageCapCcls, List<OperatingUnit> servedOUs, Map<String, Map<String, int[]>> initialStorageLevels) {
        this.FSCname = FSCname;
        this.maxStorageCapCcls = maxStorageCapCcls;
        this.servedOUs = servedOUs;
        this.initialStorageLevels = initialStorageLevels;
    }
}
