package Objects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Holds all outputs for one solved instance of the capacitated resupply
 * problem.
 * The class is used for the visual representation of a solution.
 */
public class Result {
    private final String instanceName;

    private final int gurobiStatus;
    private final boolean optimal;

    private final Double objectiveValue;

    private final int trucksAtMsc;
    private final int[] trucksAtFsc;

    // For each FSC, list of OUs it supplies
    private final List<List<String>> ousSuppliedByFsc;

    // Variables used for visualisation
    public record XKey(String fsc, String ou, int cclType, int t) {}
    public record YKey(String fsc, int cclType, String ouType, int t) {}
    public record ZKey(int cclType, int t) {}
    public record IKey(String ou, String product, int t) {}
    public record SKey(String fsc, int cclType, String ouType, int t) {}

    private final int M_value; // M
    private final Map<String, Integer> K_value; // K[w]
    private final Map<XKey, Integer> x_value; // x[w,i,c,t]
    private final Map<YKey, Integer> y_value; // y[w,c,o,t]
    private final Map<ZKey, Integer> z_value; // z[c,t]
    private final Map<IKey, Double> I_value; // I[i,p,t]
    private final Map<SKey, Integer> S_value; // S[w,c,o,t]

    /**
     * Constructor.
     * @param instanceName     name of the instance
     * @param gurobiStatus     status of Gurobi model
     * @param optimal          true if optimal, false otherwise
     * @param objectiveValue   objective value of model
     * @param trucksAtMsc      #trucks at the MSC
     * @param trucksAtFsc      #trucks at each FSC
     * @param ousSuppliedByFsc for each FSC, a list of OUs it supplies
     * @param M_value          #trucks at MSC
     * @param K_value          #trucks at each FSC
     * @param x_value          #CCLs of type c shipped from FSC w to OU i on day t
     * @param y_value          #CCLs of type c destined for OU type o shipped from
     *                         MSC to FSC w on day t
     * @param z_value          #CCLs of type c shipped from MSC to Vust on day t
     * @param I_value          inventory level of product p at OU i at the start of
     *                         day t (kg)
     * @param S_value          inventory at FSC w of CCL type c destined for OU type
     *                         o at the start of day t (#CCLs)
     */
    public Result(String instanceName,
            int gurobiStatus,
            boolean optimal,
            Double objectiveValue,
            int trucksAtMsc,
            int[] trucksAtFsc,
            List<List<String>> ousSuppliedByFsc,
            int M_value,
            Map<String, Integer> K_value,
            Map<XKey, Integer> x_value,
            Map<YKey, Integer> y_value,
            Map<ZKey, Integer> z_value,
            Map<IKey, Double> I_value,
            Map<SKey, Integer> S_value) {
        this.instanceName = instanceName;
        this.gurobiStatus = gurobiStatus;
        this.optimal = optimal;
        this.objectiveValue = objectiveValue;
        this.trucksAtMsc = trucksAtMsc;
        this.trucksAtFsc = trucksAtFsc.clone();
        this.ousSuppliedByFsc = ousSuppliedByFsc;

        this.M_value = M_value;
        this.K_value = K_value;
        this.x_value = x_value;
        this.y_value = y_value;
        this.z_value = z_value;
        this.I_value = I_value;
        this.S_value = S_value;
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
     * Returns the objective function value of the solution.
     * @return objective value
     */
    public Double getObjectiveValue() {
        return this.objectiveValue;
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
        for (int i = 0; i < 10; i++) {
            total = total + this.trucksAtFsc[i];
        }
        instanceResult.add(total);

        instanceResult.add(this.trucksAtMsc);
        for (int i = 0; i < 10; i++)
            instanceResult.add(this.trucksAtFsc[i]);
        return instanceResult;
    }

    // Below methods are used to retrieve the variables from the Gurobi model, 
    // used for the visual representation of a solution. 

    public Map<XKey, Integer> getXValue() {
        return Collections.unmodifiableMap(x_value);
    }

    public Map<YKey, Integer> getYValue() {
        return Collections.unmodifiableMap(y_value);
    }

    public Map<ZKey, Integer> getZValue() {
        return Collections.unmodifiableMap(z_value);
    }

    public Map<IKey, Double> getIValue() {
        return Collections.unmodifiableMap(I_value);
    }

    public Map<SKey, Integer> getSValue() {
        return Collections.unmodifiableMap(S_value);
    }

    public Map<String, Integer> getKValue() {
        return Collections.unmodifiableMap(K_value);
    }

    public int getMValue() {
        return M_value;
    }

}
