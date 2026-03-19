package Objects;

/**
 * Represents a stockout event: a shortage of a product at an operating unit on a given day.
 *
 * @author 621349it Ies Timmerarends
 * @author 612348ih Isabel Hellebrekers
 * @author 631426ls Lena Stiebing
 * @author 661267eb Eeke Bavelaar
 */
public class Stockout {
    private final String ou;
    private final String product;
    private final double amount;
    private final int day;

    /**
     * @param ou        the name of the operating unit where the stockout occurred
     * @param product   the product that ran short (FW, FUEL, or AMMO)
     * @param amount    the unmet demand quantity (kg)
     * @param day       the simulation day on which the stockout occurred
     */
    public Stockout(String ou, String product, double amount, int day) {
        this.ou = ou;
        this.product = product;
        this.amount = amount;
        this.day = day;
    }

    /**
     * Returns the operating unit where the stockout occurred.
     * @return the operating unit
     */
    public String getOu() {
        return ou;
    }

    /**
     * Returns the product that caused the stockout.
     * @return the product
     */
    public String getProduct() {
        return product;
    }

    /**
     * Returns the amount of shortage during the stockout.
     * @return the shortage amount
     */
    public double getAmount() {
        return amount;
    }

    /**
     * Returns the day on which the stockout occurred.
     * @return the day number
     */
    public int getDay() {
        return day;
    }

    /**
     * String representation of the stockout event.
     */
    @Override
    public String toString() {
        return "Stockout{" +
                "ou='" + ou + '\'' +
                ", product='" + product + '\'' +
                ", amount=" + amount +
                ", day=" + day +
                '}';
    }
    
}
