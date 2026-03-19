package Objects;

import java.util.Map;

/**
 * Presents a Forward Supply Centre.
 * An FSC has an initial inventory (amount of CCLs; CCL type and OU type specific),
 * and an max storage capacity (amount of CCLs).
 */
public class FSC {
    private final String name;
    private final int maxStorageCapCcls;
    private final Map<String, int[]> initialStorageLevels;

    /**
     * Constructor.
     * @param name                  the name of the FSC
     * @param maxStorageCapCcls     the maximum capacity (#CCLs)
     * @param initialStorageLevels  the initial storage (#CCLs for each CCL type and OU type)
     */
    public FSC(String name, Integer maxStorageCapCcls, Map<String, int[]> initialStorageLevels) {
        this.name = name;
        this.maxStorageCapCcls = maxStorageCapCcls;
        this.initialStorageLevels = initialStorageLevels;
    }

    /** @return the name of this FSC */
    public String getName() { return name; }

    /** @return the maximum storage capacity in CCLs */
    public int getMaxStorageCapCcls() { return maxStorageCapCcls; }

    /** @return the initial storage levels keyed by OU type */
    public Map<String, int[]> getInitialStorageLevels() { return initialStorageLevels; }
}
