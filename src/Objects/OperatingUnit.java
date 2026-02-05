package Objects;
import java.time.LocalTime;
import java.util.Arrays;

/**
 * Presents an Operating Unit (OU).
 * Quantities (except for time values) are expressed in kg.
 */
public class OperatingUnit {
    public final String operatingUnitName;
    public final String ouType;

    // Daily demand (kg)
    public final long dailyFoodWaterKg;
    public final long dailyFuelKg;
    public final long dailyAmmoKg;

    // Stochastic demand (kg)
    public final long[] stochasticFoodWaterKg;
    public final long[] stochasticFuelKg;
    public final long[] stochasticAmmoKg;

    // Maximum storage capacities at the OU (kg)
    public final long maxFoodWaterKg;
    public final long maxFuelKg;
    public final long maxAmmoKg;

    // Assigned resupply source
    public final String source;

    /**
     * Constructor.
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
    private OperatingUnit(
            String operatingUnitName,
            String ouType,
            long dailyFoodWaterKg, long dailyFuelKg, long dailyAmmoKg,
            long[] stochasticFoodWaterKg, long[] stochasticFuelKg, long[] stochasticAmmoKg,
            long maxFoodWaterKg, long maxFuelKg, long maxAmmoKg,
            String source
    ) {
        this.operatingUnitName = operatingUnitName;
        this.ouType = ouType;
        this.dailyFoodWaterKg = dailyFoodWaterKg;
        this.dailyFuelKg = dailyFuelKg;
        this.dailyAmmoKg = dailyAmmoKg;
        this.stochasticFoodWaterKg = (stochasticFoodWaterKg == null) ? null : Arrays.copyOf(stochasticFoodWaterKg, stochasticFoodWaterKg.length);
        this.stochasticFuelKg     = (stochasticFuelKg == null) ? null : Arrays.copyOf(stochasticFuelKg, stochasticFuelKg.length);
        this.stochasticAmmoKg     = (stochasticAmmoKg == null) ? null : Arrays.copyOf(stochasticAmmoKg, stochasticAmmoKg.length);
        this.maxFoodWaterKg = maxFoodWaterKg;
        this.maxFuelKg = maxFuelKg;
        this.maxAmmoKg = maxAmmoKg;
        this.source = source;
    }

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
            long dailyFoodWaterKg, long dailyFuelKg, long dailyAmmoKg,
            long maxFoodWaterKg, long maxFuelKg, long maxAmmoKg,
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
            long[] stochasticFoodWaterKg, long[] stochasticFuelKg, long[] stochasticAmmoKg,
            long maxFoodWaterKg, long maxFuelKg, long maxAmmoKg,
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

    @Override 
    public String toString() {
        return "operatingUnit='" + operatingUnitName + '\'' +
                ", ouType=" + ouType +
                ", daily(FW/Fuel/Ammo)=" + dailyFoodWaterKg + "/" + dailyFuelKg + "/" + dailyAmmoKg +
                ", max(FW/Fuel/Ammo)=" + maxFoodWaterKg + "/" + maxFuelKg + "/" + maxAmmoKg +
                ", source='" + source + '}';
    }
}