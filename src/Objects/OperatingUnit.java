package Objects;
import java.time.LocalTime;

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