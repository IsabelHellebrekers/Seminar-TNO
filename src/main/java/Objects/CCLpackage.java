package Objects;

/**
 * Presents a CCL package. 
 * A truck can carry at most one CCL. 
 * A CCL contains Food&Water, Fuel, and Ammunition (in kg).
 */
public class CCLpackage {
    public final int type;
    public final long foodWaterKg;
    public final long fuelKg;
    public final long ammoKg;

    /**
     * Constructor. 
     * @param type          CCL type 
     * @param foodWaterKg   amount of FW in CCL (kg)
     * @param fuelKg        amount of FUEL in CCL (kg)
     * @param ammoKg        amount of AMMO in CCL (kg)
     */
    public CCLpackage(int type, long foodWaterKg, long fuelKg, long ammoKg) {
        this.type = type;
        this.foodWaterKg = foodWaterKg;
        this.fuelKg = fuelKg;
        this.ammoKg = ammoKg;
    }

    /**
     * String representation of a CCL type.
     */
    @Override public String toString() {
        return "type='" + type + '\'' +
                ", FW=" + foodWaterKg +
                ", Fuel=" + fuelKg +
                ", Ammo=" + ammoKg +
                '}';
    }
}