package Objects;

/**
 * Presents a CCL package.
 * A truck can carry at most one CCL.
 * A CCL contains Food&Water, Fuel, and Ammunition (in kg).
 */
public class CCLPackage {
    private final int type;
    private final long foodWaterKg;
    private final long fuelKg;
    private final long ammoKg;

    /**
     * Constructor.
     * @param type          CCL type
     * @param foodWaterKg   amount of FW in CCL (kg)
     * @param fuelKg        amount of FUEL in CCL (kg)
     * @param ammoKg        amount of AMMO in CCL (kg)
     */
    public CCLPackage(int type, long foodWaterKg, long fuelKg, long ammoKg) {
        this.type = type;
        this.foodWaterKg = foodWaterKg;
        this.fuelKg = fuelKg;
        this.ammoKg = ammoKg;
    }

    /** @return the CCL type number */
    public int getType() { return type; }

    /** @return the food/water content of this CCL (kg) */
    public long getFoodWaterKg() { return foodWaterKg; }

    /** @return the fuel content of this CCL (kg) */
    public long getFuelKg() { return fuelKg; }

    /** @return the ammunition content of this CCL (kg) */
    public long getAmmoKg() { return ammoKg; }

    /**
     * String representation of a CCL type.
     */
    @Override
    public String toString() {
        return "type='" + type + '\'' +
                ", FW=" + foodWaterKg +
                ", Fuel=" + fuelKg +
                ", Ammo=" + ammoKg +
                '}';
    }
}
