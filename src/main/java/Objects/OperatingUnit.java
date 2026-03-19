package Objects;

/**
 * Represents a military Operating Unit (OU) that consumes supplies and must be
 * resupplied by an FSC or the MSC. Stores daily demand rates and maximum storage
 * capacities for Food &amp; Water, Fuel, and Ammunition. The stochastic variant
 * holds a pre-sampled demand array (one value per day) instead of a fixed rate.
 * Quantities (except for time values) are expressed in kg.
 *
 * @author 621349it Ies Timmerarends
 * @author 612348ih Isabel Hellebrekers
 * @author 631426ls Lena Stiebing
 * @author 661267eb Eeke Bavelaar
 */
public class OperatingUnit {
    private final String operatingUnitName;
    private final String ouType;

    // Daily demand (kg)
    private final double dailyFoodWaterKg;
    private final double dailyFuelKg;
    private final double dailyAmmoKg;

    // Stochastic demand (kg)
    private final double[] stochasticFoodWaterKg;
    private final double[] stochasticFuelKg;
    private final double[] stochasticAmmoKg;

    // Maximum storage capacities at the OU (kg)
    private final double maxFoodWaterKg;
    private final double maxFuelKg;
    private final double maxAmmoKg;

    // Assigned resupply source (MSC or one of the FSCs)
    private String source;

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
        this.stochasticFoodWaterKg = null;
        this.stochasticFuelKg = null;
        this.stochasticAmmoKg = null;
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
        this.stochasticFoodWaterKg = stochasticFoodWaterKg;
        this.stochasticFuelKg = stochasticFuelKg;
        this.stochasticAmmoKg = stochasticAmmoKg;
        this.maxFoodWaterKg = maxFoodWaterKg;
        this.maxFuelKg = maxFuelKg;
        this.maxAmmoKg = maxAmmoKg;
        this.source = source;
    }

    /** @return the name of this operating unit */
    public String getName() { return operatingUnitName; }

    /** @return the type of this operating unit */
    public String getOuType() { return ouType; }

    /** @return the daily food/water demand (kg) */
    public double getDailyFoodWaterKg() { return dailyFoodWaterKg; }

    /** @return the daily fuel demand (kg) */
    public double getDailyFuelKg() { return dailyFuelKg; }

    /** @return the daily ammunition demand (kg) */
    public double getDailyAmmoKg() { return dailyAmmoKg; }

    /** @return the stochastic food/water demand array (kg) */
    public double[] getStochasticFoodWaterKg() { return stochasticFoodWaterKg; }

    /** @return the stochastic fuel demand array (kg) */
    public double[] getStochasticFuelKg() { return stochasticFuelKg; }

    /** @return the stochastic ammunition demand array (kg) */
    public double[] getStochasticAmmoKg() { return stochasticAmmoKg; }

    /** @return the maximum food/water storage capacity (kg) */
    public double getMaxFoodWaterKg() { return maxFoodWaterKg; }

    /** @return the maximum fuel storage capacity (kg) */
    public double getMaxFuelKg() { return maxFuelKg; }

    /** @return the maximum ammunition storage capacity (kg) */
    public double getMaxAmmoKg() { return maxAmmoKg; }

    /** @return the assigned resupply source */
    public String getSource() { return source; }

    /**
     * Reassigns the resupply source of this operating unit.
     * Used when generating partition instances where OU-to-FSC assignments vary.
     * @param newSourceName the name of the new resupply source (FSC name or "MSC")
     */
    public void changeSource(String newSourceName) {
        this.source = newSourceName;
    }

    /**
     * String representation of an operating unit.
     */
    @Override
    public String toString() {
        return "operatingUnit='" + operatingUnitName + '\'' +
                ", ouType=" + ouType +
                ", daily(FW/Fuel/Ammo)=" + dailyFoodWaterKg + "/" + dailyFuelKg + "/" + dailyAmmoKg +
                ", max(FW/Fuel/Ammo)=" + maxFoodWaterKg + "/" + maxFuelKg + "/" + maxAmmoKg +
                ", source='" + source + '}';
    }
}
