package Objects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Presents an Instance for the Capacitated Resupply Problem (CRP).
 */
public class Instance {
    public final List<OperatingUnit> operatingUnits;
    public final List<FSC> FSCs;
    public final List<String> products;
    public final List<CCLpackage> cclTypes;
    public final List<String> ouTypes;
    public final int timeHorizon;

    public Instance(List<OperatingUnit> operatingUnits, List<FSC> fscs) {
        this.operatingUnits = operatingUnits;
        this.FSCs = fscs;
        this.products = List.of("FW", "FUEL", "AMMO");
        this.cclTypes = List.of(
                new CCLpackage(1, 3000, 3000, 4000),
                new CCLpackage(2, 1000, 4000, 5000),
                new CCLpackage(3, 0, 2000, 8000));
        this.ouTypes = List.of("VUST", "GN_CIE", "PAINF_CIE", "AT_CIE");
        this.timeHorizon = 10;
    }

    public void addOperatingUnit(String operatingUnitName,
                                  String ouType,
                                  long dailyFoodWaterKg, long dailyFuelKg, long dailyAmmoKg,
                                  long maxFoodWaterKg, long maxFuelKg, long maxAmmoKg,
                                  String source) {
        this.operatingUnits.add(new OperatingUnit(operatingUnitName, ouType, dailyFoodWaterKg, dailyFuelKg, dailyAmmoKg, maxFoodWaterKg, maxFuelKg, maxAmmoKg, source));
    }

    public void addFSC(String name, int maxStorageCapCcls, Map<String, int[]> initialStorageLevels) {
        this.FSCs.add(new FSC(name, maxStorageCapCcls, initialStorageLevels));
    }
}
