package Analysis;

import Objects.CCLpackage;
import Objects.Instance;
import java.util.Map;

/**
 * Bundles an Instance with its fleet configuration (MSC trucks and per-FSC
 * truck allocation), a human-readable label, and an optional fourth CCL type.
 * Use {@link #of} for configs without a fourth CCL type.
 */
public record InstanceConfig(Instance instance, int mscTrucks, Map<String, Integer> trucksPerFSC, String label,
        CCLpackage fourthCCL) {

    /** Convenience factory for configs without a fourth CCL type. */
    public static InstanceConfig of(Instance instance, int mscTrucks, Map<String, Integer> trucksPerFSC,
            String label) {
        return new InstanceConfig(instance, mscTrucks, trucksPerFSC, label, null);
    }
}
