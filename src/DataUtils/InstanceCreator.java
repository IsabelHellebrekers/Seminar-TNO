package DataUtils;

import Objects.FSC;
import Objects.Instance;
import Objects.OperatingUnit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InstanceCreator {
    public List<Instance> createFDInstance() {
        List<OperatingUnit> operatingUnits = new ArrayList<>();
        operatingUnits.add(new OperatingUnit("VUST", "VUST", 13018, 42842, 67140, 39054, 128526, 201420, "MSC"));
        operatingUnits.add(new OperatingUnit("GN_CIE_1", "GN_CIE", 6209, 21072, 43718, 18627, 63216, 131154, "FSC_1"));
        operatingUnits.add(new OperatingUnit("GN_CIE_2", "GN_CIE", 6966, 24558, 39476, 20898, 73674, 118428, "FSC_2"));
        operatingUnits.add(new OperatingUnit("PAINF_CIE_1", "PAINF_CIE", 8086, 31693, 58222, 24258, 95079, 174666, "FSC_1"));
        operatingUnits.add(new OperatingUnit("PAINF_CIE_2", "PAINF_CIE", 11197, 26127, 60676, 33591, 78381, 182028, "FSC_1"));
        operatingUnits.add(new OperatingUnit("PAINF_CIE_3", "PAINF_CIE", 9447, 31049, 57504, 28341, 93147, 172512, "FSC_1"));
        operatingUnits.add(new OperatingUnit("PAINF_CIE_4", "PAINF_CIE", 11570, 33486, 52944, 34710, 100458, 158832, "FSC_2"));
        operatingUnits.add(new OperatingUnit("PAINF_CIE_5", "PAINF_CIE", 10351, 31801, 55848, 31053, 95403, 167544, "FSC_2"));
        operatingUnits.add(new OperatingUnit("AT_CIE_1", "AT_CIE", 3476, 7921, 17603, 10428, 23763, 52809, "FSC_1"));
        operatingUnits.add(new OperatingUnit("AT_CIE_2", "AT_CIE", 2484, 7898, 18618, 7452, 23694, 55854, "FSC_1"));
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

    // MAKE CONTIGUOUS PARTITIONS
    public List<Instance> contiguousPartitions() {
        Instance base = createFDInstance().getFirst();

        // Make list of OUs based on base
        List<OperatingUnit> remainingOUs = new ArrayList<>();

        for (OperatingUnit ou : base.operatingUnits) {
            if (ou.operatingUnitName.equals("VUST")) continue;
            remainingOUs.add(ou);
        }

        int initialDepth = 1;

        // Call recursive function
        return makePartition(remainingOUs, initialDepth);
    }

    public ArrayList<Instance> makePartition(List<OperatingUnit> remainingOUs, int depth) {
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

    public void addOperatingUnitsToNode(Instance inst, List<OperatingUnit> subList, String sourceName) {
        for (OperatingUnit ou : subList) {
            inst.addOperatingUnit(ou.operatingUnitName, ou.ouType, ou.dailyFoodWaterKg, ou.dailyFuelKg, ou.dailyAmmoKg, ou.maxFoodWaterKg, ou.maxFuelKg, ou.maxAmmoKg, sourceName);
        }
    }

    public void addFSCToNode(Instance inst, int depth, List<OperatingUnit> subList) {
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

//    // MAKE PARTITIONS BASED ON CURRENT FSCS (NOT USED)
//    public List<Instance> currentFSCpartitions() {
//        Instance base = createFDInstance().getFirst();
//
//        // Categorise the OUs with the FSC
//        List<String> fsc1Names = ouNamesBySource(base, "FSC_1");
//        List<String> fsc2Names = ouNamesBySource(base, "FSC_2");
//
//        // Initial storage per FSC for every OU
//        Map<String, int[]> init1 = initialStorage(7, 29, 6);
//        Map<String, int[]> init2 = initialStorage(7, 19, 3);
//
//        // All possible splits of each FSC into smaller FSCs. all partitions -> all small FSCs in one partition -> OUs of one small FSC -> OU
//        List<List<List<String>>> fsc1Splits = partitions(fsc1Names, 6);
//        List<List<List<String>>> fsc2Splits = partitions(fsc2Names, 4);
//
//        List<Instance> all = new ArrayList<>(fsc1Splits.size() * fsc2Splits.size());
//        System.out.println(fsc1Splits.size() * fsc2Splits.size());
//
//        for (List<List<String>> p1 : fsc1Splits) {
//            for (List<List<String>> p2 : fsc2Splits) {
//                all.add(buildInstance(base, p1, p2, init1, init2));
//            }
//        }
//
//        return all;
//    }
//
//    private static List<String> ouNamesBySource(Instance base, String source) {
//        List<String> names = new ArrayList<>();
//        for (OperatingUnit ou : base.operatingUnits) {
//            if (ou.operatingUnitName.equals("VUST")) continue;
//            if (ou.source.equals(source)) names.add(ou.operatingUnitName);
//        }
//        return names;
//    }
//
//    private static Map<String, int[]> initialStorage(int gn, int painf, int at) {
//        Map<String, int[]> init = new HashMap<>();
//        init.put("GN_CIE", new int[]{gn, gn, gn});
//        init.put("PAINF_CIE", new int[]{painf, painf, painf});
//        init.put("AT_CIE", new int[]{at, at, at});
//        return init;
//    }
//
//    /**
//     * Generate all set partitions of items into at most maxBlocks blocks.
//     */
//    private static List<List<List<String>>> partitions(List<String> OUs, int maxBlocks) {
//        List<List<List<String>>> result = new ArrayList<>();
//        buildPartitions(OUs, 0, new ArrayList<>(), result, maxBlocks);
//        return result;
//    }
//
//    private static void buildPartitions(List<String> OUs, int idx, List<List<String>> currentPartitions, List<List<List<String>>> resultPartitions, int maxBlocks) {
//
//        if (idx == OUs.size()) {
//            // deep copy
//            List<List<String>> copy = new ArrayList<>();
//            for (List<String> b : currentPartitions) copy.add(new ArrayList<>(b));
//            resultPartitions.add(copy);
//            return;
//        }
//
//        String x = OUs.get(idx);
//
//        // Put x into existing blocks.
//        for (int i = 0; i < currentPartitions.size(); i++) {
//            List<String> b = currentPartitions.get(i);
//            b.add(x);
//            buildPartitions(OUs, idx + 1, currentPartitions, resultPartitions, maxBlocks);
//            b.remove(b.size() - 1);
//        }
//
//        // Start a new block (if allowed).
//        if (currentPartitions.size() < maxBlocks) {
//            List<String> nb = new ArrayList<>();
//            nb.add(x);
//            currentPartitions.add(nb);
//            buildPartitions(OUs, idx + 1, currentPartitions, resultPartitions, maxBlocks);
//            currentPartitions.remove(currentPartitions.size() - 1);
//        }
//    }
//
//    private static Instance buildInstance(Instance base, List<List<String>> fsc1Groups, List<List<String>> fsc2Groups, Map<String, int[]> init1, Map<String, int[]> init2) {
//        List<FSC> newFSCs = new ArrayList<>();
//        newFSCs.addAll(makeFSCs("FSC_1", fsc1Groups, 126, init1));
//        newFSCs.addAll(makeFSCs("FSC_2", fsc2Groups, 87, init2));
//
//        Map<String, String> newSource = new HashMap<>();
//        newSource.putAll(groupToSource("FSC_1", fsc1Groups));
//        newSource.putAll(groupToSource("FSC_2", fsc2Groups));
//
//        List<OperatingUnit> newOUs = new ArrayList<>(base.operatingUnits.size());
//        for (OperatingUnit ou : base.operatingUnits) {
//            if (ou.operatingUnitName.equals("VUST")) {
//                newOUs.add(ou);
//                continue;
//            }
//            newOUs.add(new OperatingUnit(
//                    ou.operatingUnitName,
//                    ou.ouType,
//                    ou.dailyFoodWaterKg, ou.dailyFuelKg, ou.dailyAmmoKg,
//                    ou.maxFoodWaterKg, ou.maxFuelKg, ou.maxAmmoKg,
//                    newSource.get(ou.operatingUnitName)
//            ));
//        }
//        return new Instance(newOUs, newFSCs);
//    }
//
//    /** Build split FSCs with equal-share capacity + equal-share initial storage. */
//    private static List<FSC> makeFSCs(String parent, List<List<String>> groups, int totalCap, Map<String, int[]> parentInit) {
//        int m = groups.size();
//        int[] caps = splitEven(totalCap, m);
//
//        // split initial storage evenly per type and per CCL dimension
//        Map<String, int[]>[] initByGroup = new HashMap[m];
//        for (int g = 0; g < m; g++) initByGroup[g] = new HashMap<>();
//
//        for (Map.Entry<String, int[]> e : parentInit.entrySet()) {
//            String type = e.getKey();
//            int[] vec = e.getValue(); // length 3
//            int[] a0 = splitEven(vec[0], m);
//            int[] a1 = splitEven(vec[1], m);
//            int[] a2 = splitEven(vec[2], m);
//            for (int g = 0; g < m; g++) {
//                initByGroup[g].put(type, new int[]{a0[g], a1[g], a2[g]});
//            }
//        }
//
//        List<FSC> out = new ArrayList<>();
//        for (int g = 0; g < m; g++) {
//            out.add(new FSC(parent + "_" + (g + 1), caps[g], initByGroup[g]));
//        }
//        return out;
//    }
//
//    /** Split total into m parts as evenly as possible (first parts get the remainder). */
//    private static int[] splitEven(int total, int m) {
//        int[] r = new int[m];
//        int base = total / m;
//        int rem = total % m;
//        for (int i = 0; i < m; i++) r[i] = base + (i < rem ? 1 : 0);
//        return r;
//    }
//
//    private static Map<String, String> groupToSource(String parent, List<List<String>> groups) {
//        Map<String, String> out = new HashMap<>();
//        for (int g = 0; g < groups.size(); g++) {
//            String fName = parent + "_" + (g + 1);
//            for (String ouName : groups.get(g)) out.put(ouName, fName);
//        }
//        return out;
//    }

}
