package Objects;
import java.time.LocalTime;

/**
 * Presents an Operating Unit (OU).
 * Quantities (except for time values) are expressed in kg.
 */
public class OperatingUnit {
    public final String operatingUnit;
    public final OuType ouType;

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

    public final LocalTime orderTime;        
    public final String timeWindow;          
    public final int drivingTimeToSourceSec; 

    /**
     * Constructor.
     * @param operatingUnit             name of the OU
     * @param ouType                    type of the OU
     * @param dailyFoodWaterKg          daily FW demand (kg)
     * @param dailyFuelKg               daily FUEL demand (kg)
     * @param dailyAmmoKg               daily AMMO demand (kg)
     * @param maxFoodWaterKg            max storage capacity FW (kg)
     * @param maxFuelKg                 max storage capacity FUEL (kg)
     * @param maxAmmoKg                 max storage capacity AMMO (kg)
     * @param source                    assigned resupply source
     * @param orderTime                 order time
     * @param timeWindow                time window to deliver supplies
     * @param drivingTimeToSourceSec    time in minuts to go from source to OU
     */
    public OperatingUnit(
            String operatingUnit,
            OuType ouType,
            long dailyFoodWaterKg, long dailyFuelKg, long dailyAmmoKg,
            long maxFoodWaterKg, long maxFuelKg, long maxAmmoKg,
            String source, LocalTime orderTime, String timeWindow, int drivingTimeToSourceSec
    ) {
        this.operatingUnit = operatingUnit;
        this.ouType = ouType;
        this.dailyFoodWaterKg = dailyFoodWaterKg;
        this.dailyFuelKg = dailyFuelKg;
        this.dailyAmmoKg = dailyAmmoKg;
        this.maxFoodWaterKg = maxFoodWaterKg;
        this.maxFuelKg = maxFuelKg;
        this.maxAmmoKg = maxAmmoKg;
        this.source = source;
        this.orderTime = orderTime;
        this.timeWindow = timeWindow;
        this.drivingTimeToSourceSec = drivingTimeToSourceSec;
    }

    @Override 
    public String toString() {
        return "operatingUnit='" + operatingUnit + '\'' +
                ", ouType=" + ouType +
                ", daily(FW/Fuel/Ammo)=" + dailyFoodWaterKg + "/" + dailyFuelKg + "/" + dailyAmmoKg +
                ", max(FW/Fuel/Ammo)=" + maxFoodWaterKg + "/" + maxFuelKg + "/" + maxAmmoKg +
                ", source='" + source + '\'' +
                ", orderTime=" + orderTime +
                ", timeWindow='" + timeWindow + '\'' +
                ", drivingTimeSec=" + drivingTimeToSourceSec +
                '}';
    }
}