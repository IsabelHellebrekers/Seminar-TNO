package Objects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public Result(String instanceName, int gurobiStatus, boolean optimal, Double objectiveValue, int trucksAtMsc, int[] trucksAtFsc, List<List<String>> ousSuppliedByFsc) {
        this.instanceName = instanceName;
        this.gurobiStatus = gurobiStatus;
        this.optimal = optimal;
        this.objectiveValue = objectiveValue;
        this.trucksAtMsc = trucksAtMsc;
        this.trucksAtFsc = trucksAtFsc.clone();
        this.ousSuppliedByFsc = ousSuppliedByFsc;
    }

    private static List<List<String>> emptyOuLists() {
        List<List<String>> lists = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) lists.add(new ArrayList<>());
        return lists;
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
}
