package Objects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Holds all outputs for one solved instance.
 */
public class Result {
    private final String instanceName;

    private final int gurobiStatus;
    private final boolean optimal;

    private final Double objectiveValue;

    private final int trucksAtMsc;
    private final int[] trucksAtFsc;

    // For each FSC_1..FSC_10, list of OUs it supplies
    private final List<List<String>> ousSuppliedByFsc;

    // Visualisation
    public record XKey(String fsc, String ou, int cclType, int t) {}
    public record YKey(String fsc, int cclType, String ouType, int t) {}
    public record ZKey(int cclType, int t) {}
    public record IKey(String ou, String product, int t) {}
    public record SKey(String fsc, int cclType, String ouType, int t) {}

    private final int M_value;
    private final Map<String, Integer> K_value;               // K[w]
    private final Map<XKey, Integer> x_value;                 // x[w,i,c,t]
    private final Map<YKey, Integer> y_value;                 // y[w,c,o,t] (optional)
    private final Map<ZKey, Integer> z_value;                 // z[c,t] (optional)
    private final Map<IKey, Double> I_value;                  // I[i,p,t]
    private final Map<SKey, Integer> S_value;                 // S[w,c,o,t] (optional)

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
                  Map<SKey, Integer> S_value){
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


    public String getInstanceName() { return this.instanceName; }
    public int getGurobiStatus() { return this.gurobiStatus; }
    public boolean isOptimal() { return this.optimal; }
    public Double getObjectiveValue() { return this.objectiveValue; }
    public long getTrucksAtMsc() { return this.trucksAtMsc; }

    public long getTrucksAtFsc(int fscIndex) {
        return this.trucksAtFsc[fscIndex - 1];
    }

    public List<String> getOusSuppliedByFsc(int fscIndex) {
        return Collections.unmodifiableList(this.ousSuppliedByFsc.get(fscIndex - 1));
    }

    /** Returns [M, K_FSC_1, ..., K_FSC_10] */
    public List<Integer> getTruckVector() {
        List<Integer> instanceResult = new ArrayList<>();

        int total = this.trucksAtMsc;
        for (int i = 0; i < 10; i++) {
            total = total + this.trucksAtFsc[i];
        }
        instanceResult.add(total);

        instanceResult.add(this.trucksAtMsc);
        for (int i = 0; i < 10; i++) instanceResult.add(this.trucksAtFsc[i]);
        return instanceResult;
    }

    // For simulation
    public Map<XKey, Integer> getXValue() { return Collections.unmodifiableMap(x_value); }
    public Map<YKey, Integer> getYValue() { return Collections.unmodifiableMap(y_value); }
    public Map<ZKey, Integer> getZValue() { return Collections.unmodifiableMap(z_value); }
    public Map<IKey, Double> getIValue() { return Collections.unmodifiableMap(I_value); }
    public Map<SKey, Integer> getSValue() { return Collections.unmodifiableMap(S_value); }
    public Map<String, Integer> getKValue() { return Collections.unmodifiableMap(K_value); }
    public int getMValue() { return M_value; }

}
