package Objects;

public class CCLpackage {
    public final String type; // Type 1, Type 2, ...
    public final long foodWaterKg;
    public final long fuelKg;
    public final long ammoKg;

    public CCLpackage(String type, long foodWaterKg, long fuelKg, long ammoKg) {
        this.type = type;
        this.foodWaterKg = foodWaterKg;
        this.fuelKg = fuelKg;
        this.ammoKg = ammoKg;
    }

    @Override public String toString() {
        return "type='" + type + '\'' +
                ", FW=" + foodWaterKg +
                ", Fuel=" + fuelKg +
                ", Ammo=" + ammoKg +
                '}';
    }
}