package Objects;

/**
 * Presents an Operating Unit (OU).
 * Quantities (except for time values) are expressed in kg.
 */
public class OperatingUnit {
    public final String operatingUnitName;
    public final String ouType;

    // Daily demand (kg)
    public final double dailyFoodWaterKg;
    public final double dailyFuelKg;
    public final double dailyAmmoKg;

    // Storage levels (kg)
    public double storageLevelFWKg;
    public double storageLevelFuelKg;
    public double storageLevelAmmoKg;

    // Maximum storage capacities at the OU (kg)
    public final double maxFoodWaterKg;
    public final double maxFuelKg;
    public final double maxAmmoKg;

    // Assigned resupply source
    public String source;

    /**
     * Deterministic constructor.
     * @param operatingUnitName         name of the OU
     * @param ouType                    type of the OU
     * @param dailyFoodWaterKg          daily FW demand (kg)
     * @param dailyFuelKg               daily FUEL demand (kg)
     * @param dailyAmmoKg               daily AMMO demand (kg)
     * @param maxFoodWaterKg            max storage capacity FW (kg)
     * @param maxFuelKg                 max storage capacity FUEL (kg)
     * @param maxAmmoKg                 max storage capacity AMMO (kg)
     * @param source                    assigned resupply source
     */
    public OperatingUnit(
            String operatingUnitName,
            String ouType,
            double dailyFoodWaterKg, double dailyFuelKg, double dailyAmmoKg,
            double maxFoodWaterKg, double maxFuelKg, double maxAmmoKg,
            String source
    ) {
        this.operatingUnitName = operatingUnitName;
        this.ouType = ouType;

        this.dailyFoodWaterKg = dailyFoodWaterKg;
        this.dailyFuelKg = dailyFuelKg;
        this.dailyAmmoKg = dailyAmmoKg;

        this.storageLevelFWKg = dailyFoodWaterKg;
        this.storageLevelFuelKg = dailyFuelKg;
        this.storageLevelAmmoKg = dailyAmmoKg;

        this.maxFoodWaterKg = maxFoodWaterKg;
        this.maxFuelKg = maxFuelKg;
        this.maxAmmoKg = maxAmmoKg;

        this.source = source;
    }

    /**
     * Stochastic constructor.
     * @param operatingUnitName         name of the OU
     * @param ouType                    type of the OU
     * @param stochasticFoodWaterKg     stochastic FW demand (kg)
     * @param stochasticFuelKg          stochastic FUEL demand (kg)
     * @param stochasticAmmoKg          stochastic AMMO demand (kg)
     * @param maxFoodWaterKg            max storage capacity FW (kg)
     * @param maxFuelKg                 max storage capacity FUEL (kg)
     * @param maxAmmoKg                 max storage capacity AMMO (kg)
     * @param source                    assigned resupply source
     */
    public OperatingUnit(
            String operatingUnitName,
            String ouType,
            double[] stochasticFoodWaterKg, double[] stochasticFuelKg, double[] stochasticAmmoKg,
            double maxFoodWaterKg, double maxFuelKg, double maxAmmoKg,
            String source
    ) {
        this.operatingUnitName = operatingUnitName;
        this.ouType = ouType;

        this.dailyFoodWaterKg = 0L;
        this.dailyFuelKg = 0L;
        this.dailyAmmoKg = 0L;

        this.storageLevelFWKg = dailyFoodWaterKg;
        this.storageLevelFuelKg = dailyFuelKg;
        this.storageLevelAmmoKg = dailyAmmoKg;

        this.maxFoodWaterKg = maxFoodWaterKg;
        this.maxFuelKg = maxFuelKg;
        this.maxAmmoKg = maxAmmoKg;

        this.source = source;
    }

    public void changeSource(String newSourceName) {
        this.source = newSourceName;
    }
    @Override 
    public String toString() {
        return "operatingUnit='" + operatingUnitName + '\'' +
                ", ouType=" + ouType +
                ", daily(FW/Fuel/Ammo)=" + dailyFoodWaterKg + "/" + dailyFuelKg + "/" + dailyAmmoKg +
                ", max(FW/Fuel/Ammo)=" + maxFoodWaterKg + "/" + maxFuelKg + "/" + maxAmmoKg +
                ", source='" + source + '}';
    }
}