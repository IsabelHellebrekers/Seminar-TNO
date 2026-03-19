package Objects;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Holds all input data for one instance of the Capacitated Resupply Problem (CRP):
 * the set of operating units, FSCs, product types, CCL package types, OU types,
 * and the planning horizon. Mutable so that OUs and FSCs can be added incrementally
 * (used when building partition instances in {@link DataUtils.InstanceCreator}).
 *
 * @author 621349it Ies Timmerarends
 * @author 612348ih Isabel Hellebrekers
 * @author 631426ls Lena Stiebing
 * @author 661267eb Eeke Bavelaar
 */
public class Instance {
    private final List<OperatingUnit> operatingUnits;
    private final List<FSC> FSCs;
    private final List<String> products;
    private final List<CCLPackage> cclTypes;
    private final List<String> ouTypes;
    private final int timeHorizon;

    /**
     * Baseline constructor with default 10-day horizon.
     * @param operatingUnits    the operating units (OUs)
     * @param fscs              the forward supply centres (FSCs)
     */
    public Instance(List<OperatingUnit> operatingUnits, List<FSC> fscs) {
        this(operatingUnits, fscs, 10);
    }

    /**
     * Constructor with custom time horizon.
     * @param operatingUnits    the operating units (OUs)
     * @param fscs              the forward supply centres (FSCs)
     * @param timeHorizon       planning horizon in days
     */
    public Instance(List<OperatingUnit> operatingUnits, List<FSC> fscs, int timeHorizon) {
        this.operatingUnits = operatingUnits;
        this.FSCs = fscs;
        this.products = List.of("FW", "FUEL", "AMMO");
        this.cclTypes = new ArrayList<>(List.of(
                new CCLPackage(1, 3000, 3000, 4000),
                new CCLPackage(2, 1000, 4000, 5000),
                new CCLPackage(3, 0, 2000, 8000)));
        this.ouTypes = List.of("VUST", "GN_CIE", "PAINF_CIE", "AT_CIE");
        this.timeHorizon = timeHorizon;
    }

    /**
     * Baseline constructor with custom CCL types and default 10-day horizon.
     * @param operatingUnits    the operating units (OUs)
     * @param fscs              the forward supply centres (FSCs)
     * @param cclTypes          the CCL types (differ in amount (kg) of each product)
     */
    public Instance(List<OperatingUnit> operatingUnits, List<FSC> fscs, List<CCLPackage> cclTypes) {
        this(operatingUnits, fscs, cclTypes, 10);
    }

    /**
     * Constructor with custom CCL types and custom time horizon.
     * @param operatingUnits    the operating units (OUs)
     * @param fscs              the forward supply centres (FSCs)
     * @param cclTypes          the CCL types (differ in amount (kg) of each product)
     * @param timeHorizon       planning horizon in days
     */
    public Instance(List<OperatingUnit> operatingUnits, List<FSC> fscs, List<CCLPackage> cclTypes, int timeHorizon) {
        this.operatingUnits = operatingUnits;
        this.FSCs = fscs;
        this.products = List.of("FW", "FUEL", "AMMO");
        this.cclTypes = new ArrayList<>(cclTypes);
        this.ouTypes = List.of("VUST", "GN_CIE", "PAINF_CIE", "AT_CIE");
        this.timeHorizon = timeHorizon;
    }

    /** @return the operating units (OUs) */
    public List<OperatingUnit> getOperatingUnits() { return operatingUnits; }

    /** @return the forward supply centres (FSCs) */
    public List<FSC> getFSCs() { return FSCs; }

    /** @return the product types */
    public List<String> getProducts() { return products; }

    /** @return the CCL package types */
    public List<CCLPackage> getCclTypes() { return cclTypes; }

    /** @return the OU types */
    public List<String> getOuTypes() { return ouTypes; }

    /** @return the planning horizon in days */
    public int getTimeHorizon() { return timeHorizon; }

    /**
     * Adds an operating unit with deterministic daily demands to this instance.
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
        this.operatingUnits.add(new OperatingUnit(
                operatingUnitName,
                ouType,
                dailyFoodWaterKg,
                dailyFuelKg,
                dailyAmmoKg,
                maxFoodWaterKg,
                maxFuelKg,
                maxAmmoKg,
                source));
    }

    /**
     * Adds a CCL package type to the instance. If a CCL with the same type
     * number already exists it is not added again.
     * @param cclPackage the CCL package type to add
     */
    public void addCCLType(CCLPackage cclPackage) {
        for (CCLPackage existing : this.cclTypes) {
            if (existing.getType() == cclPackage.getType()) {
                return;
            }
        }
        this.cclTypes.add(cclPackage);
    }

    /**
     * Adds a forward supply centre with the given name, capacity, and initial inventory.
     * @param name                  name of the forward supply centre
     * @param maxStorageCapCcls     maximum capacity (#CCLs)
     * @param initialStorageLevels  initial storage (#CCLs for each CCL type and OU type)
     */
    public void addFSC(String name, int maxStorageCapCcls, Map<String, int[]> initialStorageLevels) {
        this.FSCs.add(new FSC(name, maxStorageCapCcls, initialStorageLevels));
    }
}
