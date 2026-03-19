package Objects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Holds all outputs for one solved instance of the capacitated resupply
 * problem.
 * The class is used for the visual representation of a solution.
 *
 * @author 621349it Ies Timmerarends
 * @author 612348ih Isabel Hellebrekers
 * @author 631426ls Lena Stiebing
 * @author 661267eb Eeke Bavelaar
 */
public class Result {
    private final String instanceName;

    private final int gurobiStatus;
    private final boolean optimal;

    private final int trucksAtMsc;
    private final int[] trucksAtFsc;

    // For each FSC, list of OUs it supplies
    private final List<List<String>> ousSuppliedByFsc;

    // Variables used for visualisation
    /** Key for x[w,i,c,t]: CCLs of type c shipped from FSC w to OU i on day t. */
    public record XKey(String fsc, String ou, int cclType, int t) {}
    /** Key for y[w,c,o,t]: CCLs of type c for OU type o shipped MSC to FSC w on day t. */
    public record YKey(String fsc, int cclType, String ouType, int t) {}
    /** Key for z[c,t]: CCLs of type c shipped from MSC to VUST on day t. */
    public record ZKey(int cclType, int t) {}
    /** Key for I[i,p,t]: inventory of product p at OU i at the start of day t. */
    public record IKey(String ou, String product, int t) {}
    /** Key for S[w,c,o,t]: inventory at FSC w of CCL type c for OU type o at start of day t. */
    public record SKey(String fsc, int cclType, String ouType, int t) {}

    // M: number of trucks at MSC
    private final int mValue;
    // K[w]: number of trucks at FSC w
    private final Map<String, Integer> kValue;
    // x[w,i,c,t]: CCLs of type c shipped from FSC w to OU i on day t
    private final Map<XKey, Integer> xValue;
    // y[w,c,o,t]: CCLs of type c for OU type o shipped from MSC to FSC w on day t
    private final Map<YKey, Integer> yValue;
    // z[c,t]: CCLs of type c shipped from MSC to VUST on day t
    private final Map<ZKey, Integer> zValue;
    // I[i,p,t]: inventory of product p at OU i at start of day t (kg)
    private final Map<IKey, Double> iValue;
    // S[w,c,o,t]: inventory at FSC w of CCL type c for OU type o at start of day t (#CCLs)
    private final Map<SKey, Integer> sValue;

    /**
     * Stores the full solution of one solved instance for downstream use (visualisation, reporting).
     * @param instanceName     name of the instance
     * @param gurobiStatus     Gurobi status code returned after solving
     * @param optimal          true if the solver found a proven optimal solution
     * @param objectiveValue   optimal objective value (total trucks), or null if not optimal
     * @param trucksAtMsc      number of trucks stationed at the MSC (from Gurobi M variable)
     * @param trucksAtFsc      number of trucks at each FSC, in a fixed-size array of length 10
     * @param ousSuppliedByFsc for each FSC (index 0..9), the sorted list of OUs it supplies
     * @param mValue          M variable value: trucks at MSC
     * @param kValue          K variable values: trucks at each FSC, keyed by FSC name
     * @param xValue          x variable values: CCLs shipped FSC->OU per (FSC, OU, CCL type, day)
     * @param yValue          y variable values: CCLs shipped MSC->FSC per (FSC, CCL type, OU type, day)
     * @param zValue          z variable values: CCLs shipped MSC->VUST per (CCL type, day)
     * @param iValue          I variable values: OU inventory of product p at start of day t (kg)
     * @param sValue          S variable values: FSC CCL inventory by OU type at start of day t
     */
    public Result(String instanceName,
            int gurobiStatus,
            boolean optimal,
            Double objectiveValue,
            int trucksAtMsc,
            int[] trucksAtFsc,
            List<List<String>> ousSuppliedByFsc,
            int mValue,
            Map<String, Integer> kValue,
            Map<XKey, Integer> xValue,
            Map<YKey, Integer> yValue,
            Map<ZKey, Integer> zValue,
            Map<IKey, Double> iValue,
            Map<SKey, Integer> sValue) {
        this.instanceName = instanceName;
        this.gurobiStatus = gurobiStatus;
        this.optimal = optimal;
        this.trucksAtMsc = trucksAtMsc;
        this.trucksAtFsc = trucksAtFsc.clone();
        this.ousSuppliedByFsc = ousSuppliedByFsc;

        this.mValue = mValue;
        this.kValue = kValue;
        this.xValue = xValue;
        this.yValue = yValue;
        this.zValue = zValue;
        this.iValue = iValue;
        this.sValue = sValue;
    }

    /**
     * Returns the name of the instance.
     * @return name of the instance
     */
    public String getInstanceName() {
        return this.instanceName;
    }

    /**
     * Returns the Gurobi status of the solved model. 
     * @return Gurobi status code
     */
    public int getGurobiStatus() {
        return this.gurobiStatus;
    }

    /**
     * Checks whether the solution is optimal.
     * @return true if optimal, false otherwise
     */
    public boolean isOptimal() {
        return this.optimal;
    }

    /**
     * Returns the number of trucks stationed at the MSC.
     * @return number of trucks at MSC
     */
    public long getTrucksAtMsc() {
        return this.trucksAtMsc;
    }

    /**
     * Returns the number of trucks stationed at a specific FSC.
     * @param fscIndex index of the FSC (1-based)
     * @return number of trucks at the FSC
     */
    public long getTrucksAtFsc(int fscIndex) {
        return this.trucksAtFsc[fscIndex - 1];
    }

    /**
     * Returns the list of operating units (OUs) supplied by a specific FSC.
     * @param fscIndex index of the FSC (1-based)
     * @return list of OUs supplied by this FSC
     */
    public List<String> getOusSuppliedByFsc(int fscIndex) {
        return Collections.unmodifiableList(this.ousSuppliedByFsc.get(fscIndex - 1));
    }

    /**
     * Returns the truck vector in the form [total, M, K_FSC_1, ..., K_FSC_10].
     * @return list with total number of trucks and distribution across MSC and FSCs
     */
    public List<Integer> getTruckVector() {
        List<Integer> instanceResult = new ArrayList<>();

        int total = this.trucksAtMsc;
        for (int i = 0; i < this.trucksAtFsc.length; i++) {
            total = total + this.trucksAtFsc[i];
        }
        instanceResult.add(total);

        instanceResult.add(this.trucksAtMsc);
        for (int i = 0; i < this.trucksAtFsc.length; i++) {
            instanceResult.add(this.trucksAtFsc[i]);
        }
        return instanceResult;
    }

    // Below methods are used to retrieve the variables from the Gurobi model,
    // used for the visual representation of a solution.

    /**
     * Returns the x-variable map: CCLs of type c shipped from FSC w to OU i on day t.
     *
     * @return unmodifiable map from XKey to CCL count
     */
    public Map<XKey, Integer> getXValue() {
        return Collections.unmodifiableMap(xValue);
    }

    /**
     * Returns the y-variable map: CCLs of type c for OU type o shipped from MSC to FSC w on day t.
     *
     * @return unmodifiable map from YKey to CCL count
     */
    public Map<YKey, Integer> getYValue() {
        return Collections.unmodifiableMap(yValue);
    }

    /**
     * Returns the z-variable map: CCLs of type c shipped from MSC to VUST on day t.
     *
     * @return unmodifiable map from ZKey to CCL count
     */
    public Map<ZKey, Integer> getZValue() {
        return Collections.unmodifiableMap(zValue);
    }

    /**
     * Returns the I-variable map: inventory level of product p at OU i at the start of day t (kg).
     *
     * @return unmodifiable map from IKey to inventory level in kg
     */
    public Map<IKey, Double> getIValue() {
        return Collections.unmodifiableMap(iValue);
    }

    /**
     * Returns the S-variable map: inventory at FSC w of CCL type c for OU type o at the start of day t.
     *
     * @return unmodifiable map from SKey to CCL count
     */
    public Map<SKey, Integer> getSValue() {
        return Collections.unmodifiableMap(sValue);
    }

    /**
     * Returns the K-variable map: number of trucks stationed at each FSC.
     *
     * @return unmodifiable map from FSC name to truck count
     */
    public Map<String, Integer> getKValue() {
        return Collections.unmodifiableMap(kValue);
    }

    /**
     * Returns the M-variable value: number of trucks stationed at the MSC.
     *
     * @return truck count at MSC
     */
    public int getMValue() {
        return mValue;
    }

}
