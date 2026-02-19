package Objects;

public class Stockout {
    private final String ou;
    private final String product;
    private final double amount;
    private final int day;

    public Stockout(String ou, String product, double amount, int day) {
        this.ou = ou;
        this.product = product;
        this.amount = amount;
        this.day = day;
    }

    public String getOu() {
        return ou;
    }

    public String getProduct() {
        return product;
    }

    public double getAmount() {
        return amount;
    }

    public int getDay() {
        return day;
    }

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
