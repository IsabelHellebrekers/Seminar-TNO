package Objects;

/**
 * Represents a CCL (Combat Consumables Load) package type.
 * Each CCL specifies the kg content of Food &amp; Water, Fuel, and Ammunition.
 * A single truck carries at most one CCL per trip, so the CCL type determines
 * what product mix is delivered in one truck movement.
 *
 * @author 621349it Ies Timmerarends
 * @author 612348ih Isabel Hellebrekers
 * @author 631426ls Lena Stiebing
 * @author 661267eb Eeke Bavelaar
 */
public class CCLPackage {
    private final int type;
    private final long foodWaterKg;
    private final long fuelKg;
    private final long ammoKg;

    /**
     * Create a CCL package type with the given product contents.
     * @param type          CCL type number (used as identifier)
     * @param foodWaterKg   food/water content of this CCL type (kg)
     * @param fuelKg        fuel content of this CCL type (kg)
     * @param ammoKg        ammunition content of this CCL type (kg)
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
