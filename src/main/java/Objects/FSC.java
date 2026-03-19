package Objects;

import java.util.Map;

/**
 * Represents a Forward Supply Centre (FSC) in the supply network.
 * An FSC acts as an intermediate depot between the MSC and operating units.
 * It holds an initial inventory of CCLs broken down by OU type and CCL type,
 * and is subject to a maximum total storage capacity (expressed in CCL count).
 *
 * @author 621349it Ies Timmerarends
 * @author 612348ih Isabel Hellebrekers
 * @author 631426ls Lena Stiebing
 * @author 661267eb Eeke Bavelaar
 */
public class FSC {
    private final String name;
    private final int maxStorageCapCcls;
    private final Map<String, int[]> initialStorageLevels;

    /**
     * Create an FSC with the given name, storage limit, and initial CCL inventory.
     * @param name                  the name of the FSC
     * @param maxStorageCapCcls     the maximum total storage capacity in CCL units
     * @param initialStorageLevels  initial CCL counts keyed by OU type, each entry
     *                              being an array indexed by CCL type (0-based)
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
