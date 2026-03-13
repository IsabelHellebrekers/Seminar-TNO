package Analysis;

import Objects.Instance;
import java.util.Map;

/**
 * Bundles an Instance with its fleet configuration (MSC trucks and per-FSC
 * truck allocation) and a human-readable label used in analysis output.
 */
public record InstanceConfig(Instance instance, int mscTrucks, Map<String, Integer> trucksPerFSC, String label) {}
