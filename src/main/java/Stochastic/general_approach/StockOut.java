package Stochastic.general_approach;

/**
 * Stockout event aggregated per FSC and time period.
 */
public class StockOut {
    private final String ouName;
    private final int timePeriod;
    private final double deficitKg;

    public StockOut(String ouName, int timePeriod, double deficitKg) {
        this.ouName = ouName;
        this.timePeriod = timePeriod;
        this.deficitKg = deficitKg;
    }

    public String getOuName() {
        return ouName;
    }

    public int getTimePeriod() {
        return timePeriod;
    }

    public double getDeficitKg() {
        return deficitKg;
    }

    @Override
    public String toString() {
        return "StockOut{" +
                "ouName='" + ouName + '\'' +
                ", timePeriod=" + timePeriod +
                ", deficitKg=" + deficitKg +
                '}';
    }
}
