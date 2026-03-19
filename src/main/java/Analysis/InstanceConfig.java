package Analysis;

import Objects.CCLPackage;
import Objects.Instance;
import java.util.Map;

/**
 * Bundles an Instance with its fleet configuration (MSC trucks and per-FSC
 * truck allocation), a human-readable label, and an optional fourth CCL type.
 * Use {@link #of} for configs without a fourth CCL type.
 *
 * @author 621349it Ies Timmerarends
 * @author 612348ih Isabel Hellebrekers
 * @author 631426ls Lena Stiebing
 * @author 661267eb Eeke Bavelaar
 */
public record InstanceConfig(Instance instance, int mscTrucks, Map<String, Integer> trucksPerFSC, String label,
        CCLPackage fourthCCL) {

    /** Convenience factory for configs without a fourth CCL type. */
    public static InstanceConfig of(Instance instance, int mscTrucks, Map<String, Integer> trucksPerFSC,
            String label) {
        return new InstanceConfig(instance, mscTrucks, trucksPerFSC, label, null);
    }
}
