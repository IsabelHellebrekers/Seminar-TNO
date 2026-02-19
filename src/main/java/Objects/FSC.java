package Objects;

import java.util.Map;

public class FSC {
    public final String FSCname;
    public final int maxStorageCapCcls;
    public final Map<String, int[]> initialStorageLevels;
    public Map<String, int[]> storageLevels;


    public FSC(String FSCname, Integer maxStorageCapCcls, Map<String, int[]> initialStorageLevels) {
        this.FSCname = FSCname;
        this.maxStorageCapCcls = maxStorageCapCcls;
        this.initialStorageLevels = initialStorageLevels;
        this.storageLevels = initialStorageLevels;
    }

    public void updateStorageLevels(Map<String, int[]> newStorageLevels) {
        this.storageLevels = newStorageLevels;
    }
}
