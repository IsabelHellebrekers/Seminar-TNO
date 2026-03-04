package Objects;

import java.util.Map;

/**
 * Presents a Forward Supply Centre. 
 * An FSC has an initial inventory (amount of CCLs; CCL type and OU type specific), 
 * and an max storage capacity (amount of CCLs).
 */
public class FSC {
    public final String FSCname;
    public final int maxStorageCapCcls;
    public final Map<String, int[]> initialStorageLevels;

    /**
     * Constructor.
     * @param FSCname               the name of the FSC
     * @param maxStorageCapCcls     the maximum capacity (#CCLs)
     * @param initialStorageLevels  the initial storage (#CCLs for each CCL type and OU type)
     */
    public FSC(String FSCname, Integer maxStorageCapCcls, Map<String, int[]> initialStorageLevels) {
        this.FSCname = FSCname;
        this.maxStorageCapCcls = maxStorageCapCcls;
        this.initialStorageLevels = initialStorageLevels;
    }
}
