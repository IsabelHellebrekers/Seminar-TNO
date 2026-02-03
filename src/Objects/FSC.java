package Objects;

import java.util.List;
import java.util.Map;

public class FSC {
    public final String FSCname;
    public final Integer maxStorageCapCcls;
    public final Map<String, int[]> initialStorageLevels;


    public FSC(String FSCname, Integer maxStorageCapCcls, Map<String, int[]> initialStorageLevels) {
        this.FSCname = FSCname;
        this.maxStorageCapCcls = maxStorageCapCcls;
        this.initialStorageLevels = initialStorageLevels;
    }
}
