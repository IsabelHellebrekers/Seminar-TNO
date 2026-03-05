package Objects;

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

    /**
     * Presents an instance for the capacitated resupply problem. 
     * @param operatingUnits    the operating units (OUs)
     * @param fscs              the forward supply centres (FSCs)
     */
    public Instance(List<OperatingUnit> operatingUnits, List<FSC> fscs) {
        this.operatingUnits = operatingUnits;
        this.FSCs = fscs;
        // Different products in the CCL packages
        this.products = List.of("FW", "FUEL", "AMMO");
        // Three standard CCL packages and the amount (kg) of each product
        this.cclTypes = List.of(
                new CCLpackage(1, 3000, 3000, 4000),
                new CCLpackage(2, 1000, 4000, 5000),
                new CCLpackage(3, 0, 2000, 8000));
        // The operating unit types
        this.ouTypes = List.of("VUST", "GN_CIE", "PAINF_CIE", "AT_CIE");
        // Time horizon of ten days (baseline)
        this.timeHorizon = 10;
    }

    /**
     * Constructor
     * @param operatingUnits    the operating units (OUs)
     * @param fscs              the forward supply centres (FSCs)
     * @param cclTypes          the CCL types (differ in amount (kg) of each product)
     */
    public Instance(List<OperatingUnit> operatingUnits, List<FSC> fscs, List<CCLpackage> cclTypes) {
        this.operatingUnits = operatingUnits;
        this.FSCs = fscs;
        this.products = List.of("FW", "FUEL", "AMMO");
        this.cclTypes = cclTypes;
        this.ouTypes = List.of("VUST", "GN_CIE", "PAINF_CIE", "AT_CIE");
        this.timeHorizon = 10;
    }

    /**
     * Method that adds an operating unit (OU) to the instance
     * @param operatingUnitName     name of the operating unit
     * @param ouType                type of the operating unit
     * @param dailyFoodWaterKg      daily FW demand (kg)
     * @param dailyFuelKg           daily FUEL demand (kg)
     * @param dailyAmmoKg           daily AMMO demand (kg)
     * @param maxFoodWaterKg        maximum capacity of FW (kg)
     * @param maxFuelKg             maximum capacity of FUEL (kg)
     * @param maxAmmoKg             maximum capacity of AMMO (kg)
     * @param source                the source of the operating unit (MSC or one of the FSCs)
     */
    public void addOperatingUnit(String operatingUnitName,
            String ouType,
            double dailyFoodWaterKg, double dailyFuelKg, double dailyAmmoKg,
            double maxFoodWaterKg, double maxFuelKg, double maxAmmoKg,
            String source) {
        this.operatingUnits.add(new OperatingUnit(operatingUnitName, ouType, dailyFoodWaterKg, dailyFuelKg, dailyAmmoKg,
                maxFoodWaterKg, maxFuelKg, maxAmmoKg, source));
    }

    /**
     * Method that adds a forward supply centre (FSC) to the instance
     * @param name                  name of the forward supply centre
     * @param maxStorageCapCcls     maximum capacity (#CCLs)
     * @param initialStorageLevels  initial storage (#CCLs for each CCL type and OU type)
     */
    public void addFSC(String name, int maxStorageCapCcls, Map<String, int[]> initialStorageLevels) {
        this.FSCs.add(new FSC(name, maxStorageCapCcls, initialStorageLevels));
    }
}
