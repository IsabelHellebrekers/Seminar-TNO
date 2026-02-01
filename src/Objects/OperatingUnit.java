package Objects;
import java.time.LocalTime;

public class OperatingUnit {
    public final String operatingUnit;
    public final OuType ouType;

    public final long dailyFoodWaterKg;
    public final long dailyFuelKg;
    public final long dailyAmmoKg;

    public final long maxFoodWaterKg;
    public final long maxFuelKg;
    public final long maxAmmoKg;

    public final String source;              // e.g., MSC, FSC 1, FSC 2
    public final LocalTime orderTime;        // e.g., 18:00
    public final String timeWindow;          // e.g., 22:00-00:00
    public final int drivingTimeToSourceSec; // e.g., 3997

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

    @Override public String toString() {
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