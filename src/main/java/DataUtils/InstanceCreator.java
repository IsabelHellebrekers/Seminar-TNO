package DataUtils;

import Objects.CCLPackage;
import Objects.FSC;
import Objects.Instance;
import Objects.OperatingUnit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class that creates instances for the capacitated resupply problem.
 */
public class InstanceCreator {

    /**
     * Creates FD instance with a default time horizon of 10 days.
     * 
     * @return FD instance
     */
    public static List<Instance> createFDInstance() {
        return createFDInstance(10);
    }

    /**
     * Creates FD instance with a custom time horizon.
     * 
     * @param timeHorizon time horizon in days
     * @return FD instance with custom time horizon
     */
    public static List<Instance> createFDInstance(int timeHorizon) {
        List<OperatingUnit> operatingUnits = new ArrayList<>();
        operatingUnits.add(new OperatingUnit("VUST", "VUST", 13018, 42842, 67140, 39054, 128526, 201420, "MSC"));
        operatingUnits.add(new OperatingUnit("GN_CIE_1", "GN_CIE", 6209, 21072, 43718, 18627, 63216, 131154, "FSC_1"));
        operatingUnits
                .add(new OperatingUnit("PAINF_CIE_1", "PAINF_CIE", 8086, 31693, 58222, 24258, 95079, 174666, "FSC_1"));
        operatingUnits
                .add(new OperatingUnit("PAINF_CIE_2", "PAINF_CIE", 11197, 26127, 60676, 33591, 78381, 182028, "FSC_1"));
        operatingUnits
                .add(new OperatingUnit("PAINF_CIE_3", "PAINF_CIE", 9447, 31049, 57504, 28341, 93147, 172512, "FSC_1"));
        operatingUnits.add(new OperatingUnit("AT_CIE_1", "AT_CIE", 3476, 7921, 17603, 10428, 23763, 52809, "FSC_1"));
        operatingUnits.add(new OperatingUnit("AT_CIE_2", "AT_CIE", 2484, 7898, 18618, 7452, 23694, 55854, "FSC_1"));
        operatingUnits.add(new OperatingUnit("GN_CIE_2", "GN_CIE", 6966, 24558, 39476, 20898, 73674, 118428, "FSC_2"));
        operatingUnits.add(
                new OperatingUnit("PAINF_CIE_4", "PAINF_CIE", 11570, 33486, 52944, 34710, 100458, 158832, "FSC_2"));
        operatingUnits
                .add(new OperatingUnit("PAINF_CIE_5", "PAINF_CIE", 10351, 31801, 55848, 31053, 95403, 167544, "FSC_2"));
        operatingUnits.add(new OperatingUnit("AT_CIE_3", "AT_CIE", 3033, 9628, 16339, 9099, 28884, 49017, "FSC_2"));

        List<FSC> fscs = new ArrayList<>();

        // Make FSC1
        Map<String, int[]> initialStorageLevelsFSC1 = new HashMap<>();
        initialStorageLevelsFSC1.put("GN_CIE", new int[] { 7, 7, 7 });
        initialStorageLevelsFSC1.put("PAINF_CIE", new int[] { 29, 29, 29 });
        initialStorageLevelsFSC1.put("AT_CIE", new int[] { 6, 6, 6 });
        fscs.add(new FSC("FSC_1", 126, initialStorageLevelsFSC1));

        // Make FSC2
        Map<String, int[]> initialStorageLevelsFSC2 = new HashMap<>();
        initialStorageLevelsFSC2.put("GN_CIE", new int[] { 7, 7, 7 });
        initialStorageLevelsFSC2.put("PAINF_CIE", new int[] { 19, 19, 19 });
        initialStorageLevelsFSC2.put("AT_CIE", new int[] { 3, 3, 3 });
        fscs.add(new FSC("FSC_2", 87, initialStorageLevelsFSC2));

        List<Instance> instanceList = new ArrayList<>();
        instanceList.add(new Instance(operatingUnits, fscs, timeHorizon));
        return instanceList;
    }

    /**
     * Creates FD instance with a fourth CCL type and a default time horizon of 10
     * days.
     * 
     * @param fw4   amount of FW (kg)
     * @param fuel4 amount of FUEL (kg)
     * @param ammo4 amount of AMMO (kg)
     * @return
     */
    public static List<Instance> createFDInstanceExtraType(int fw4, int fuel4, int ammo4) {
        return createFDInstanceExtraType(fw4, fuel4, ammo4, 10);
    }

    /**
     * Creates FD instance with a fourth CCL type and a custom time horizon.
     * 
     * @param fw4         amount of FW (kg)
     * @param fuel4       amount of FUEL (kg)
     * @param ammo4       amount of AMMO (kg)
     * @param timeHorizon time horizon in days
     * @return
     */
    public static List<Instance> createFDInstanceExtraType(int fw4, int fuel4, int ammo4, int timeHorizon) {
        List<OperatingUnit> operatingUnits = new ArrayList<>();
        operatingUnits.add(new OperatingUnit("VUST", "VUST", 13018, 42842, 67140, 39054, 128526, 201420, "MSC"));
        operatingUnits.add(new OperatingUnit("GN_CIE_1", "GN_CIE", 6209, 21072, 43718, 18627, 63216, 131154, "FSC_1"));
        operatingUnits
                .add(new OperatingUnit("PAINF_CIE_1", "PAINF_CIE", 8086, 31693, 58222, 24258, 95079, 174666, "FSC_1"));
        operatingUnits
                .add(new OperatingUnit("PAINF_CIE_2", "PAINF_CIE", 11197, 26127, 60676, 33591, 78381, 182028, "FSC_1"));
        operatingUnits
                .add(new OperatingUnit("PAINF_CIE_3", "PAINF_CIE", 9447, 31049, 57504, 28341, 93147, 172512, "FSC_1"));
        operatingUnits.add(new OperatingUnit("AT_CIE_1", "AT_CIE", 3476, 7921, 17603, 10428, 23763, 52809, "FSC_1"));
        operatingUnits.add(new OperatingUnit("AT_CIE_2", "AT_CIE", 2484, 7898, 18618, 7452, 23694, 55854, "FSC_1"));
        operatingUnits.add(new OperatingUnit("GN_CIE_2", "GN_CIE", 6966, 24558, 39476, 20898, 73674, 118428, "FSC_2"));
        operatingUnits.add(
                new OperatingUnit("PAINF_CIE_4", "PAINF_CIE", 11570, 33486, 52944, 34710, 100458, 158832, "FSC_2"));
        operatingUnits
                .add(new OperatingUnit("PAINF_CIE_5", "PAINF_CIE", 10351, 31801, 55848, 31053, 95403, 167544, "FSC_2"));
        operatingUnits.add(new OperatingUnit("AT_CIE_3", "AT_CIE", 3033, 9628, 16339, 9099, 28884, 49017, "FSC_2"));

        List<FSC> fscs = new ArrayList<>();

        Map<String, int[]> initialStorageLevelsFSC1 = new HashMap<>();
        initialStorageLevelsFSC1.put("GN_CIE", new int[] { 5, 5, 5, 6 });
        initialStorageLevelsFSC1.put("PAINF_CIE", new int[] { 21, 22, 22, 22 });
        initialStorageLevelsFSC1.put("AT_CIE", new int[] { 4, 4, 5, 5 });
        fscs.add(new FSC("FSC_1", 126, initialStorageLevelsFSC1));

        Map<String, int[]> initialStorageLevelsFSC2 = new HashMap<>();
        initialStorageLevelsFSC2.put("GN_CIE", new int[] { 5, 5, 5, 6 });
        initialStorageLevelsFSC2.put("PAINF_CIE", new int[] { 14, 14, 14, 15 });
        initialStorageLevelsFSC2.put("AT_CIE", new int[] { 2, 2, 2, 3 });
        fscs.add(new FSC("FSC_2", 87, initialStorageLevelsFSC2));

        List<CCLPackage> cclTypes = List.of(
                new CCLPackage(1, 3000, 3000, 4000),
                new CCLPackage(2, 1000, 4000, 5000),
                new CCLPackage(3, 0, 2000, 8000),
                new CCLPackage(4, fw4, fuel4, ammo4));

        List<Instance> instanceList = new ArrayList<>();
        instanceList.add(new Instance(operatingUnits, fscs, cclTypes, timeHorizon));
        return instanceList;
    }

    public static List<Instance> createDispersedInstanceExtraType(int fw4, int fuel4, int ammo4, int timeHorizon) {
        List<OperatingUnit> operatingUnits = new ArrayList<>();
        operatingUnits.add(new OperatingUnit("VUST", "VUST", 13018, 42842, 67140, 39054, 128526, 201420, "MSC"));
        operatingUnits.add(new OperatingUnit("GN_CIE_1", "GN_CIE", 6209, 21072, 43718, 18627, 63216, 131154, "FSC_1"));
        operatingUnits
                .add(new OperatingUnit("PAINF_CIE_1", "PAINF_CIE", 8086, 31693, 58222, 24258, 95079, 174666, "FSC_1"));
        operatingUnits
                .add(new OperatingUnit("PAINF_CIE_2", "PAINF_CIE", 11197, 26127, 60676, 33591, 78381, 182028, "FSC_1"));
        operatingUnits
                .add(new OperatingUnit("PAINF_CIE_3", "PAINF_CIE", 9447, 31049, 57504, 28341, 93147, 172512, "FSC_2"));
        operatingUnits.add(new OperatingUnit("AT_CIE_1", "AT_CIE", 3476, 7921, 17603, 10428, 23763, 52809, "FSC_2"));
        operatingUnits.add(new OperatingUnit("AT_CIE_2", "AT_CIE", 2484, 7898, 18618, 7452, 23694, 55854, "FSC_3"));
        operatingUnits.add(new OperatingUnit("GN_CIE_2", "GN_CIE", 6966, 24558, 39476, 20898, 73674, 118428, "FSC_3"));
        operatingUnits.add(
                new OperatingUnit("PAINF_CIE_4", "PAINF_CIE", 11570, 33486, 52944, 34710, 100458, 158832, "FSC_4"));
        operatingUnits
                .add(new OperatingUnit("PAINF_CIE_5", "PAINF_CIE", 10351, 31801, 55848, 31053, 95403, 167544, "FSC_4"));
        operatingUnits.add(new OperatingUnit("AT_CIE_3", "AT_CIE", 3033, 9628, 16339, 9099, 28884, 49017, "FSC_4"));

        List<FSC> fscs = new ArrayList<>();

        Map<String, int[]> initialStorageLevelsFSC1 = new HashMap<>();
        initialStorageLevelsFSC1.put("GN_CIE", new int[] { 5, 5, 5, 6 });
        initialStorageLevelsFSC1.put("PAINF_CIE", new int[] { 14, 14, 14, 15 });
        initialStorageLevelsFSC1.put("AT_CIE", new int[] { 0, 0, 0, 0 });
        fscs.add(new FSC("FSC_1",78, initialStorageLevelsFSC1));

        Map<String, int[]> initialStorageLevelsFSC2 = new HashMap<>();
        initialStorageLevelsFSC2.put("GN_CIE", new int[] { 0, 0, 0, 0 });
        initialStorageLevelsFSC2.put("PAINF_CIE", new int[] { 7, 7, 8, 8 });
        initialStorageLevelsFSC2.put("AT_CIE", new int[] { 2, 2, 2, 3 });
        fscs.add(new FSC("FSC_2", 39, initialStorageLevelsFSC2));

        Map<String, int[]> initialStorageLevelsFSC3 = new HashMap<>();
        initialStorageLevelsFSC3.put("GN_CIE", new int[] { 5, 5, 5, 6 });
        initialStorageLevelsFSC3.put("PAINF_CIE", new int[] { 0, 0, 0, 0 });
        initialStorageLevelsFSC3.put("AT_CIE", new int[] { 2, 2, 2, 3 });
        fscs.add(new FSC("FSC_3", 30, initialStorageLevelsFSC3));

        Map<String, int[]> initialStorageLevelsFSC4 = new HashMap<>();
        initialStorageLevelsFSC4.put("GN_CIE", new int[] { 0, 0, 0, 0 });
        initialStorageLevelsFSC4.put("PAINF_CIE", new int[] { 14, 14, 14, 15 });
        initialStorageLevelsFSC4.put("AT_CIE", new int[] { 2, 2, 2, 3 });
        fscs.add(new FSC("FSC_4", 66, initialStorageLevelsFSC4));

        // Map<String, int[]> initialStorageLevelsFSC5 = new HashMap<>();
        // initialStorageLevelsFSC5.put("GN_CIE", new int[] { 0, 0, 0, 0 });
        // initialStorageLevelsFSC5.put("PAINF_CIE", new int[] { 7, 7, 8, 8 });
        // initialStorageLevelsFSC5.put("AT_CIE", new int[] { 2, 2, 2, 3});
        // fscs.add(new FSC("FSC_5", 39, initialStorageLevelsFSC5));

        List<CCLPackage> cclTypes = List.of(
                new CCLPackage(1, 3000, 3000, 4000),
                new CCLPackage(2, 1000, 4000, 5000),
                new CCLPackage(3, 0, 2000, 8000),
                new CCLPackage(4, fw4, fuel4, ammo4));

        List<Instance> instanceList = new ArrayList<>();
        instanceList.add(new Instance(operatingUnits, fscs, cclTypes, timeHorizon));
        return instanceList;
    }

    /**
     * Creates all contiguous partition instances of the FD case.
     * Each partition represents a valid assignment of operating units to FSCs where
     * OUs
     * supplied by the same FSC form a contiguous group. Vust is added to every
     * partition.
     * 
     * @return list of all contiguous partition instances
     */
    public static List<Instance> contiguousPartitions() {
        Instance base = createFDInstance().get(0);

        OperatingUnit vust = null;

        // Make list of OUs based on base
        List<OperatingUnit> remainingOUs = new ArrayList<>();

        for (OperatingUnit ou : base.operatingUnits) {
            if (ou.operatingUnitName.equals("VUST")) {
                vust = ou;
            } else {
                remainingOUs.add(ou);
            }
        }

        int initialDepth = 1;
        ArrayList<Instance> partitions = makePartition(remainingOUs, initialDepth);

        // Add VUST to every partition
        if (vust != null) {
            for (Instance inst : partitions) {
                inst.addOperatingUnit(
                        vust.operatingUnitName,
                        vust.ouType,
                        vust.dailyFoodWaterKg,
                        vust.dailyFuelKg,
                        vust.dailyAmmoKg,
                        vust.maxFoodWaterKg,
                        vust.maxFuelKg,
                        vust.maxAmmoKg,
                        "MSC");
            }
        }
        return partitions;
    }

    /**
     * Recursively generates all contiguous partitions of operating units into FSCs.
     * At each recursion level, the first batch of OUs is assigned to an FSC, and
     * the
     * remaining OUs are partitione recursively at the next FSC level.
     * 
     * @param remainingOUs list of OUs not yet assigned to any FSC
     * @param depth        the FSC level (FSC_1, FSC_2, etc.)
     * @return list of all valid instanced that can be constructed from the
     *         remaining OUs
     */
    public static ArrayList<Instance> makePartition(List<OperatingUnit> remainingOUs, int depth) {
        if (remainingOUs.isEmpty()) {
            ArrayList<Instance> base = new ArrayList<>();
            base.add(new Instance(new ArrayList<>(), new ArrayList<>()));
            return base;
        }

        // Instantiate final list
        ArrayList<Instance> instancesAtNode = new ArrayList<>();

        // Make splits and call recursion
        for (int i = 1; i <= remainingOUs.size(); i++) {
            // Add Operating units to Instance
            ArrayList<Instance> currentList = makePartition(remainingOUs.subList(i, remainingOUs.size()), depth + 1);

            for (Instance inst : currentList) {
                // Add FSC
                addFSCToNode(inst, depth, remainingOUs.subList(0, i));

                // Add operating Units
                addOperatingUnitsToNode(inst, remainingOUs.subList(0, i), "FSC_" + depth);
            }

            // merge Instance lists
            instancesAtNode.addAll(currentList);
        }
        return instancesAtNode;
    }

    /**
     * Adds operating units to an instance, all assigned to the specified source
     * FSC.
     * 
     * @param inst       the instance to add operating units to
     * @param subList    the list of operating units to add
     * @param sourceName the FSC name that will supply these operating units
     */
    public static void addOperatingUnitsToNode(Instance inst, List<OperatingUnit> subList, String sourceName) {
        for (OperatingUnit ou : subList) {
            inst.addOperatingUnit(ou.operatingUnitName, ou.ouType, ou.dailyFoodWaterKg, ou.dailyFuelKg, ou.dailyAmmoKg,
                    ou.maxFoodWaterKg, ou.maxFuelKg, ou.maxAmmoKg, sourceName);
        }
    }

    // Helper function for makePartition to add an FSC to an instance with the
    // correct capacity and initial storage levels based on the OUs in the sublist.

    /**
     * Adds an FSC to an instance with automatically calculated capacity and initial
     * inventory levels.
     * Initial storage levels are calculated based on the count and types of
     * operating units in the sublist.
     * 
     * @param inst    the instance to add the FSC to
     * @param depth   the FSC level number, used to create FSC name (FSC_depth)
     * @param subList the list of OUs that this FSC will supply
     */
    public static void addFSCToNode(Instance inst, int depth, List<OperatingUnit> subList) {
        // Calculate initialStorageLevels
        Map<String, int[]> initialStorageLevelsFSC = new HashMap<String, int[]>();

        int amountGN = 0;
        int amountPAINF = 0;
        int amountAT = 0;

        for (OperatingUnit ou : subList) {
            if (ou.ouType.equals("GN_CIE"))
                amountGN++;
            if (ou.ouType.equals("PAINF_CIE"))
                amountPAINF++;
            if (ou.ouType.equals("AT_CIE"))
                amountAT++;
        }

        int totalGN = amountGN * 7;
        int oddPAINF = (amountPAINF + 1) / 2;
        int evenPAINF = amountPAINF / 2;
        int totalPAINF = oddPAINF * 10 + evenPAINF * 9;
        int totalAT = amountAT * 3;

        initialStorageLevelsFSC.put("GN_CIE", new int[] { totalGN, totalGN, totalGN });
        initialStorageLevelsFSC.put("PAINF_CIE", new int[] { totalPAINF, totalPAINF, totalPAINF });
        initialStorageLevelsFSC.put("AT_CIE", new int[] { totalAT, totalAT, totalAT });

        // Calculate maxStorageCapCcls
        int maxStorageCapCcls = 3 * (totalGN + totalPAINF + totalAT);

        // Add FSC
        inst.addFSC("FSC_" + depth, maxStorageCapCcls, initialStorageLevelsFSC);
    }

}
