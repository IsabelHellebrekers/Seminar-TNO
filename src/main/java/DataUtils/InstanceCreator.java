package DataUtils;

import Objects.FSC;
import Objects.Instance;
import Objects.OperatingUnit;
import Stochastic.Sampling;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InstanceCreator {
    public static List<Instance> createFDInstance() {
        List<OperatingUnit> operatingUnits = new ArrayList<>();
        operatingUnits.add(new OperatingUnit("VUST", "VUST", 13018, 42842, 67140, 39054, 128526, 201420, "MSC"));
        operatingUnits.add(new OperatingUnit("GN_CIE_1", "GN_CIE", 6209, 21072, 43718, 18627, 63216, 131154, "FSC_1"));
        operatingUnits.add(new OperatingUnit("PAINF_CIE_1", "PAINF_CIE", 8086, 31693, 58222, 24258, 95079, 174666, "FSC_1"));
        operatingUnits.add(new OperatingUnit("PAINF_CIE_2", "PAINF_CIE", 11197, 26127, 60676, 33591, 78381, 182028, "FSC_1"));
        operatingUnits.add(new OperatingUnit("PAINF_CIE_3", "PAINF_CIE", 9447, 31049, 57504, 28341, 93147, 172512, "FSC_1"));
        operatingUnits.add(new OperatingUnit("AT_CIE_1", "AT_CIE", 3476, 7921, 17603, 10428, 23763, 52809, "FSC_1"));
        operatingUnits.add(new OperatingUnit("AT_CIE_2", "AT_CIE", 2484, 7898, 18618, 7452, 23694, 55854, "FSC_1"));
        operatingUnits.add(new OperatingUnit("GN_CIE_2", "GN_CIE", 6966, 24558, 39476, 20898, 73674, 118428, "FSC_2"));
        operatingUnits.add(new OperatingUnit("PAINF_CIE_4", "PAINF_CIE", 11570, 33486, 52944, 34710, 100458, 158832, "FSC_2"));
        operatingUnits.add(new OperatingUnit("PAINF_CIE_5", "PAINF_CIE", 10351, 31801, 55848, 31053, 95403, 167544, "FSC_2"));
        operatingUnits.add(new OperatingUnit("AT_CIE_3", "AT_CIE", 3033, 9628, 16339, 9099, 28884, 49017, "FSC_2"));

        List<FSC> fscs = new ArrayList<>();

        // Make FSC1
        Map<String, int[]> initialStorageLevelsFSC1 = new HashMap<String, int[]>();
        initialStorageLevelsFSC1.put("GN_CIE", new int[]{7, 7, 7});
        initialStorageLevelsFSC1.put("PAINF_CIE", new int[]{29, 29, 29});
        initialStorageLevelsFSC1.put("AT_CIE", new int[]{6, 6, 6});
        fscs.add(new FSC("FSC_1", 126, initialStorageLevelsFSC1));

        // Make FSC2
        Map<String, int[]> initialStorageLevelsFSC2 = new HashMap<String, int[]>();
        initialStorageLevelsFSC2.put("GN_CIE", new int[]{7, 7, 7});
        initialStorageLevelsFSC2.put("PAINF_CIE", new int[]{19, 19, 19});
        initialStorageLevelsFSC2.put("AT_CIE", new int[]{3, 3, 3});
        fscs.add(new FSC("FSC_2", 87, initialStorageLevelsFSC2));

        List<Instance> instanceList = new ArrayList<>();
        instanceList.add(new Instance(operatingUnits, fscs));
        return instanceList;
    }

//    public Instance createStochasticInstanceFD() {
//        Sampling sampler = new Sampling();
//        List<OperatingUnit> operatingUnits = new ArrayList<>();
//
//        operatingUnits.add(new OperatingUnit("GN_CIE_1","GN_CIE",sampler.stochasticFW(6209),sampler.stochasticFUEL(21072),sampler.stochasticAMMO(43718),18627,63216,131154,"FSC_1"));
//        operatingUnits.add(new OperatingUnit("PAINF_CIE_1","PAINF_CIE",sampler.stochasticFW(8086),sampler.stochasticFUEL(31693),sampler.stochasticAMMO(58222),24258,95079,174666,"FSC_1"));
//        operatingUnits.add(new OperatingUnit("GN_CIE_2","GN_CIE",sampler.stochasticFW(6966),sampler.stochasticFUEL(24558),sampler.stochasticAMMO(39476),20898,73674,118428,"FSC_2"));
//        operatingUnits.add(new OperatingUnit("PAINF_CIE_2","PAINF_CIE",sampler.stochasticFW(11197),sampler.stochasticFUEL(26127),sampler.stochasticAMMO(60676),33591,78381,182028,"FSC_1"));
//        operatingUnits.add(new OperatingUnit("PAINF_CIE_3","PAINF_CIE",sampler.stochasticFW(9447),sampler.stochasticFUEL(31049),sampler.stochasticAMMO(57504),28341,93147,172512,"FSC_1"));
//        operatingUnits.add(new OperatingUnit("PAINF_CIE_4","PAINF_CIE",sampler.stochasticFW(11570),sampler.stochasticFUEL(33486),sampler.stochasticAMMO(52944),34710,100458,158832,"FSC_2"));
//        operatingUnits.add(new OperatingUnit("PAINF_CIE_5","PAINF_CIE",sampler.stochasticFW(10351),sampler.stochasticFUEL(31801),sampler.stochasticAMMO(55848),31053,95403,167544,"FSC_2"));
//        operatingUnits.add(new OperatingUnit("AT_CIE_1","AT_CIE",sampler.stochasticFW(3476),sampler.stochasticFUEL(7921),sampler.stochasticAMMO(17603),10428,23763,52809,"FSC_1"));
//        operatingUnits.add(new OperatingUnit("AT_CIE_2","AT_CIE",sampler.stochasticFW(2484),sampler.stochasticFUEL(7898),sampler.stochasticAMMO(18618),7452,23694,55854,"FSC_1"));
//        operatingUnits.add(new OperatingUnit("AT_CIE_3","AT_CIE",sampler.stochasticFW(3033),sampler.stochasticFUEL(9628),sampler.stochasticAMMO(16339),9099,28884,49017,"FSC_2"));
//
//        List<FSC> fscs = new ArrayList<>();
//
//        // Make FSC1
//        Map<String, int[]> initialStorageLevelsFSC1 = new HashMap<String, int[]>();
//        initialStorageLevelsFSC1.put("GN_CIE", new int[]{7, 7, 7});
//        initialStorageLevelsFSC1.put("PAINF_CIE", new int[]{29, 29, 29});
//        initialStorageLevelsFSC1.put("AT_CIE", new int[]{6, 6, 6});
//        fscs.add(new FSC("FSC_1", 126, initialStorageLevelsFSC1));
//
//        // Make FSC2
//        Map<String, int[]> initialStorageLevelsFSC2 = new HashMap<String, int[]>();
//        initialStorageLevelsFSC2.put("GN_CIE", new int[]{7, 7, 7});
//        initialStorageLevelsFSC2.put("PAINF_CIE", new int[]{19, 19, 19});
//        initialStorageLevelsFSC2.put("AT_CIE", new int[]{3, 3, 3});
//        fscs.add(new FSC("FSC_2", 87, initialStorageLevelsFSC2));
//
//        return new Instance(operatingUnits, fscs);
//    }

    // Make contiguous partitions of OUs into FSCs, with VUST in every partition. 
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
                    "MSC"
                );
            }
        }
        return partitions;
    }

    // Recursive function to make contiguous partitions of OUs into FSCs. 
    public static ArrayList<Instance> makePartition(List<OperatingUnit> remainingOUs, int depth) {
        // if remainingOUs = 0 --> return ArrayList<Instance>
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

    // Helper function for makePartition to add OUs to an instance with the correct source (FSC)
    public static void addOperatingUnitsToNode(Instance inst, List<OperatingUnit> subList, String sourceName) {
        for (OperatingUnit ou : subList) {
            inst.addOperatingUnit(ou.operatingUnitName, ou.ouType, ou.dailyFoodWaterKg, ou.dailyFuelKg, ou.dailyAmmoKg, ou.maxFoodWaterKg, ou.maxFuelKg, ou.maxAmmoKg, sourceName);
        }
    }

    // Helper function for makePartition to add an FSC to an instance with the correct capacity and initial storage levels based on the OUs in the sublist. 
    public static void addFSCToNode(Instance inst, int depth, List<OperatingUnit> subList) {
        // Calculate initialStorageLevels
        Map<String, int[]> initialStorageLevelsFSC = new HashMap<String, int[]>();

        int amountGN = 0;
        int amountPAINF = 0;
        int amountAT = 0;

        for (OperatingUnit ou : subList) {
            if (ou.ouType.equals("GN_CIE")) amountGN++;
            if (ou.ouType.equals("PAINF_CIE")) amountPAINF++;
            if (ou.ouType.equals("AT_CIE")) amountAT++;
        }

        int totalGN = amountGN * 7;
        int oddPAINF = (amountPAINF + 1) / 2;
        int evenPAINF = amountPAINF / 2;
        int totalPAINF = oddPAINF * 9 + evenPAINF * 10;
        int totalAT = amountAT * 3;

        initialStorageLevelsFSC.put("GN_CIE", new int[]{totalGN, totalGN, totalGN});
        initialStorageLevelsFSC.put("PAINF_CIE", new int[]{totalPAINF, totalPAINF, totalPAINF});
        initialStorageLevelsFSC.put("AT_CIE", new int[]{totalAT, totalAT, totalAT});

        // Calculate maxStorageCapCcls
        int maxStorageCapCcls = 3 * (totalGN + totalPAINF + totalAT);

        // Add FSC
        inst.addFSC("FSC_" + depth, maxStorageCapCcls, initialStorageLevelsFSC);
    }

}
