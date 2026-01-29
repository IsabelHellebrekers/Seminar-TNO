package Objects;

public class Centre {
    public final String centre;      // MSC, FSC 1, FSC 2
    public final Double maxFoodWaterKg; // Infinity allowed
    public final Double maxFuelKg;
    public final Double maxAmmoKg;
    public final Integer drivingTimeToSourceSec; // may be null/NA

    public Centre(String centre, Double maxFoodWaterKg, Double maxFuelKg, Double maxAmmoKg, Integer drivingTimeToSourceSec) {
        this.centre = centre;
        this.maxFoodWaterKg = maxFoodWaterKg;
        this.maxFuelKg = maxFuelKg;
        this.maxAmmoKg = maxAmmoKg;
        this.drivingTimeToSourceSec = drivingTimeToSourceSec;
    }

    @Override public String toString() {
        return "centre='" + centre + '\'' +
                ", max(FW/Fuel/Ammo)=" + maxFoodWaterKg + "/" + maxFuelKg + "/" + maxAmmoKg +
                ", drivingTimeSec=" + drivingTimeToSourceSec +
                '}';
    }
}