package DataUtils;

import Objects.FSC;
import Objects.Instance;
import Objects.OperatingUnit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InstanceCreator {
    public Instance createFDInstance() {
        List<OperatingUnit> operatingUnits = new ArrayList<>();
        operatingUnits.add(new OperatingUnit("VUST","VUST",13018,42842,67140,39054,128526,201420,"MSC"));
        operatingUnits.add(new OperatingUnit("GN_CIE_1","GN_CIE",6209,21072,43718,18627,63216,131154,"FSC_1"));
        operatingUnits.add(new OperatingUnit("GN_CIE_2","GN_CIE",6966,24558,39476,20898,73674,118428,"FSC_2"));
        operatingUnits.add(new OperatingUnit("PAINF_CIE_1","PAINF_CIE",8086,31693,58222,24258,95079,174666,"FSC_1"));
        operatingUnits.add(new OperatingUnit("PAINF_CIE_2","PAINF_CIE",11197,26127,60676,33591,78381,182028,"FSC_1"));
        operatingUnits.add(new OperatingUnit("PAINF_CIE_3","PAINF_CIE",9447,31049,57504,28341,93147,172512,"FSC_1"));
        operatingUnits.add(new OperatingUnit("PAINF_CIE_4","PAINF_CIE",11570,33486,52944,34710,100458,158832,"FSC_2"));
        operatingUnits.add(new OperatingUnit("PAINF_CIE_5","PAINF_CIE",10351,31801,55848,31053,95403,167544,"FSC_2"));
        operatingUnits.add(new OperatingUnit("AT_CIE_1","AT_CIE",3476,7921,17603,10428,23763,52809,"FSC_1"));
        operatingUnits.add(new OperatingUnit("AT_CIE_2","AT_CIE",2484,7898,18618,7452,23694,55854,"FSC_1"));
        operatingUnits.add(new OperatingUnit("AT_CIE_3","AT_CIE",3033,9628,16339,9099,28884,49017,"FSC_2"));

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


        return new Instance(operatingUnits, fscs);
    }
}
