package Deterministic;

import com.gurobi.gurobi.*;
import Objects.*;

import java.util.*;

/**
 * MILP for the Capacitated Resupply Problem
 */
public class CapacitatedResupplyMILP {
    private static final double EPS = 1e-9;

    private final Instance data;
    private final GRBModel model;

    // Lists of all FSCs, arcs, OUs, products, CCL types, OU Types
    private final List<FSC> fscs;
    private final List<Arc> arcs = new ArrayList<>();
    private final List<OperatingUnit> ous;
    private final List<String> products;
    private final List<CCLPackage> cclTypes;
    private final List<String> ouTypes;

    // Hashmaps to easy access the GRBVariables
    private final Map<Result.XKey, GRBVar> x = new HashMap<>();
    private final Map<Result.YKey, GRBVar> y = new HashMap<>();
    private final Map<Result.ZKey, GRBVar> z = new HashMap<>();
    private final Map<Result.IKey, GRBVar> I = new HashMap<>();
    private final Map<Result.SKey, GRBVar> S = new HashMap<>();

    // Number of trucks stationed at MSC
    private GRBVar mscTruckVar;
    // Number of trucks stationed at FSC w
    private final Map<String, GRBVar> fscTruckVars = new HashMap<>();

    // Record keys for map indexing
    private record Arc(String w, String i) {
    }

    /**
     * Build the MILP model (derive sets, create variables, add constraints and set
     * objective)
     */
    public CapacitatedResupplyMILP(Instance data, GRBEnv env, boolean verbose) throws GRBException {
        this.data = data;
        this.fscs = this.data.FSCs;
        this.ous = this.data.operatingUnits;
        this.products = this.data.products;
        this.cclTypes = this.data.cclTypes;
        this.ouTypes = this.data.ouTypes;

        buildArcs();

        env.set(GRB.IntParam.OutputFlag, 0);
        this.model = new GRBModel(env);

        buildVariables();
        buildConstraints();
        buildObjective();

        model.update();
    }

    /**
     * Build FSC -> OU arcs
     */
    private void buildArcs() {
        for (OperatingUnit ou : ous) {
            if (ou.operatingUnitName.equals("VUST")) {
                continue;
            }
            this.arcs.add(new Arc(ou.source, ou.operatingUnitName));
        }
    }

    /**
     * Create all decision variables.
     *
     * @throws GRBException if an error occurs
     */
    private void buildVariables() throws GRBException {
        // Trucks (stationed at MSC, FSC1 or FSC2)
        mscTruckVar = this.model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER, "M");

        // Build fscTruckVars for all FSCs
        for (FSC fsc : this.data.FSCs) {
            fscTruckVars.put(fsc.FSCname, this.model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER, "K_" + fsc.FSCname));
        }

        // x[w, i, c, t] : FSC -> OU deliveries
        for (Arc a : arcs) {
            String w = a.w();
            String i = a.i();
            for (CCLPackage c : this.cclTypes) {
                for (int t = 1; t <= this.data.timeHorizon; t++) {
                    Result.XKey key = new Result.XKey(w, i, c.type, t);
                    x.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER,
                            "x_{" + "w" + w + "_i" + i + "_c" + c + "_t" + t + "}"));
                }
            }
        }

        // y[w, c, o, t] : MSC -> FSC deliveries
        for (FSC fsc : fscs) {
            for (CCLPackage c : this.cclTypes) {
                for (String o : this.ouTypes) {
                    for (int t = 1; t <= this.data.timeHorizon; t++) {
                        Result.YKey key = new Result.YKey(fsc.FSCname, c.type, o, t);
                        y.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER,
                                "y_{" + "w" + fsc.FSCname + "_c" + c + "_o" + o + "_t" + t + "}"));
                    }
                }
            }
        }

        // z[c, t] : MSC -> Vust deliveries
        for (CCLPackage c : this.cclTypes) {
            for (int t = 1; t <= this.data.timeHorizon; t++) {
                Result.ZKey key = new Result.ZKey(c.type, t);
                z.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER,
                        "z_{" + "c" + c + "_t" + t + "}"));
            }
        }

        // I[i, p, t]: OU inventory (kg)
        for (OperatingUnit ou : ous) {
            String i = ou.operatingUnitName;
            for (String p : this.products) {
                for (int t = 1; t <= this.data.timeHorizon; t++) {
                    Result.IKey key = new Result.IKey(i, p, t);
                    I.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS,
                            "I_{" + "i" + i + "_p" + p + "_t" + t + "}"));
                }
            }
        }

        // S[w, c, o, t] : FSC inventory (#CCL)
        for (FSC fsc : fscs) {
            for (CCLPackage c : this.cclTypes) {
                for (String o : this.ouTypes) {
                    for (int t = 1; t <= this.data.timeHorizon; t++) {
                        Result.SKey key = new Result.SKey(fsc.FSCname, c.type, o, t);
                        S.put(key, this.model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER,
                                "S_{" + "w" + fsc.FSCname + "_c" + c + "_o" + o + "_t" + t + "}"));
                    }
                }
            }
        }
    }

    /**
     * Objective : minimize total number of trucks.
     *
     * @throws GRBException if an error occurs
     */
    private void buildObjective() throws GRBException {
        GRBLinExpr obj = new GRBLinExpr();

        obj.addTerm(1.0, this.mscTruckVar);

        for (FSC fsc : this.fscs) {
            obj.addTerm(1.0, fscTruckVars.get(fsc.FSCname));
        }

        model.setObjective(obj, GRB.MINIMIZE);
    }

    /**
     * Add all constraints to the model.
     *
     * @throws GRBException if an error occurs
     */
    private void buildConstraints() throws GRBException {
        // Truck departure capacities per day
        addTruckConstraintsFsc();
        addTruckConstraintsMsc();

        // Initial inventories
        addOuInitialInventoryConstraints();
        addFscInitialInventoryConstraints();

        // Inventory balance constraints
        addFscInventoryBalanceConstraints();
        addOuInventoryBalanceConstraints();

        // Service-level and capacity constraints
        addNoStockOutConstraints();
        addOuCapacityConstraints();
        addFscCapacityConstraints();
        addFscDispatchFeasibilityConstraints();
    }

    /**
     * Set OU starting inventories equal to their maximum storage.
     *
     * @throws GRBException if an error occurs
     */
    private void addOuInitialInventoryConstraints() throws GRBException {
        for (OperatingUnit ou : ous) {
            String i = ou.operatingUnitName;
            // Food & Water initial inventory
            model.addConstr(
                    I.get(new Result.IKey(i, "FW", 1)),
                    GRB.EQUAL,
                    ou.maxFoodWaterKg,
                    "OU_INIT_FW_" + i);

            // Fuel initial inventory
            model.addConstr(
                    I.get(new Result.IKey(i, "FUEL", 1)),
                    GRB.EQUAL,
                    ou.maxFuelKg,
                    "OU_INIT_FUEL_" + i);

            // Ammunition initial inventory
            model.addConstr(
                    I.get(new Result.IKey(i, "AMMO", 1)),
                    GRB.EQUAL,
                    ou.maxAmmoKg,
                    "OU_INIT_AMMO_" + i);
        }
    }

    /**
     * Set FSC starting inventory based on initial storage in the data.
     *
     * @throws GRBException if an error occurs
     */
    private void addFscInitialInventoryConstraints() throws GRBException {
        for (FSC w : fscs) {
            for (CCLPackage c : this.cclTypes) {
                for (String o : this.ouTypes) {
                    if (o.equals("VUST")) {
                        continue;
                    }
                    this.model.addConstr(
                            S.get(new Result.SKey(w.FSCname, c.type, o, 1)),
                            GRB.EQUAL,
                            w.initialStorageLevels.get(o)[c.type - 1],
                            "FSC_INIT_{w" + w.FSCname + "_c" + c + "_o" + o + "}");

                }
            }
        }
    }

    /**
     * FSC truck constraint (one CCL per truck per day)
     *
     * @throws GRBException if an error occurs
     */
    private void addTruckConstraintsFsc() throws GRBException {
        for (FSC w : fscs) {
            for (int t = 1; t <= this.data.timeHorizon; t++) {
                GRBLinExpr lhs = new GRBLinExpr();

                // Sum LHS
                for (Arc a : arcs) {
                    if (!a.w().equals(w.FSCname)) {
                        continue;
                    }
                    for (CCLPackage c : cclTypes) {
                        GRBVar var = x.get(new Result.XKey(w.FSCname, a.i(), c.type, t));
                        lhs.addTerm(1.0, var);
                    }
                }

                model.addConstr(
                        lhs,
                        GRB.LESS_EQUAL,
                        fscTruckVars.get(w.FSCname),
                        "TRUCK_FSC_{w" + w.FSCname + "_t" + t + "}");
            }
        }
    }

    /**
     * MSC truck constraint (one CCL per truck per day)
     *
     * @throws GRBException if an error occurs
     */
    private void addTruckConstraintsMsc() throws GRBException {
        for (int t = 1; t <= this.data.timeHorizon; t++) {
            GRBLinExpr lhs = new GRBLinExpr();

            for (FSC w : fscs) {
                for (CCLPackage c : cclTypes) {
                    for (String o : this.ouTypes) {
                        GRBVar var = y.get(new Result.YKey(w.FSCname, c.type, o, t));
                        lhs.addTerm(1.0, var);
                    }
                }
            }

            for (CCLPackage c : this.cclTypes) {
                lhs.addTerm(1.0, z.get(new Result.ZKey(c.type, t)));
            }

            model.addConstr(lhs, GRB.LESS_EQUAL, mscTruckVar, "TRUCK_MSC_t" + t);
        }
    }

    /**
     * FSC inventory balance constraints.
     *
     * @throws GRBException if an error occurs
     */
    private void addFscInventoryBalanceConstraints() throws GRBException {
        for (int t = 1; t <= this.data.timeHorizon - 1; t++) {
            for (FSC w : this.fscs) {
                for (CCLPackage c : this.cclTypes) {
                    for (String o : this.ouTypes) {
                        if (o.equals("VUST")) {
                            continue;
                        }
                        GRBLinExpr rhs = new GRBLinExpr();

                        rhs.addTerm(1.0, S.get(new Result.SKey(w.FSCname, c.type, o, t)));

                        List<OperatingUnit> ousOfType = new ArrayList<>();
                        for (OperatingUnit ou : this.ous) {
                            if (ou.ouType.equals(o)) {
                                ousOfType.add(ou);
                            }
                        }

                        for (OperatingUnit i : ousOfType) {
                            if (i.source.equals(w.FSCname)) {
                                Result.XKey xk = new Result.XKey(w.FSCname, i.operatingUnitName, c.type, t);
                                GRBVar xv = x.get(xk);
                                rhs.addTerm(-1.0, xv);
                            }
                        }

                        rhs.addTerm(1.0, y.get(new Result.YKey(w.FSCname, c.type, o, t)));

                        model.addConstr(
                                S.get(new Result.SKey(w.FSCname, c.type, o, t + 1)),
                                GRB.EQUAL,
                                rhs,
                                "FSC_BAL_{w" + w.FSCname + "_c" + c + "_o" + o + "_t" + t + "}");
                    }
                }
            }
        }
    }

    /**
     * OU inventory balance constraints.
     * MADE SOME ADJUSTMENTS FOR PERFECT HINDSIGHT EVALUATION
     *
     * @throws GRBException if an error occurs
     */
    private void addOuInventoryBalanceConstraints() throws GRBException {
        for (int t = 1; t <= this.data.timeHorizon - 1; t++) {
            for (OperatingUnit ou : this.ous) {
                String i = ou.operatingUnitName;

                for (String p : this.products) {
                    GRBLinExpr rhs = new GRBLinExpr();

                    // Start from current inventory (start-of-day t)
                    rhs.addTerm(1.0, I.get(new Result.IKey(i, p, t)));

                    // Subtract demand ONCE for day t
                    rhs.addConstant(-demandAt(ou, p, t));

                    // Add deliveries shipped on day t (available at t+1)
                    for (CCLPackage c : this.cclTypes) {
                        double content = switch (p) {
                            case "FW" -> c.foodWaterKg;
                            case "FUEL" -> c.fuelKg;
                            case "AMMO" -> c.ammoKg;
                            default -> throw new IllegalArgumentException("Unknown product: " + p);
                        };

                        if (!i.equals("VUST")) {
                            String w = ou.source; // FSC supplying this OU
                            rhs.addTerm(content, x.get(new Result.XKey(w, i, c.type, t)));
                        } else {
                            // VUST gets deliveries from MSC via z
                            rhs.addTerm(content, z.get(new Result.ZKey(c.type, t)));
                        }
                    }

                    model.addConstr(
                            I.get(new Result.IKey(i, p, t + 1)),
                            GRB.EQUAL,
                            rhs,
                            "OU_BAL_{-" + i + "_p" + p + "_t" + t + "}");
                }
            }
        }
    }

    /**
     * No stock-out constraints.
     *
     * @throws GRBException if an error occurs
     */
    private void addNoStockOutConstraints() throws GRBException {
        for (OperatingUnit ou : ous) {
            String i = ou.operatingUnitName;

            for (int t = 1; t <= this.data.timeHorizon; t++) {
                model.addConstr(
                        I.get(new Result.IKey(i, "FW", t)),
                        GRB.GREATER_EQUAL,
                        demandAt(ou, "FW", t),
                        "NOSTOCK_FW_{i" + i + "_t" + t + "}");
                model.addConstr(
                        I.get(new Result.IKey(i, "FUEL", t)),
                        GRB.GREATER_EQUAL,
                        demandAt(ou, "FUEL", t),
                        "NOSTOCK_FUEL_{i" + i + "_t" + t + "}");
                model.addConstr(
                        I.get(new Result.IKey(i, "AMMO", t)),
                        GRB.GREATER_EQUAL,
                        demandAt(ou, "AMMO", t),
                        "NOSTOCK_AMMO_{" + i + "_t" + t + "}");
            }
        }
    }

    /**
     * OU storage capacity constraints.
     *
     * @throws GRBException if an error occurs
     */
    private void addOuCapacityConstraints() throws GRBException {
        for (OperatingUnit ou : ous) {
            String i = ou.operatingUnitName;

            for (int t = 1; t <= this.data.timeHorizon; t++) {

                model.addConstr(
                        I.get(new Result.IKey(i, "FW", t)),
                        GRB.LESS_EQUAL,
                        ou.maxFoodWaterKg,
                        "CAP_FW_{i" + i + "_t" + t + "}");

                model.addConstr(
                        I.get(new Result.IKey(i, "FUEL", t)),
                        GRB.LESS_EQUAL,
                        ou.maxFuelKg,
                        "CAP_FUEL_{i" + i + "_t" + t + "}");

                model.addConstr(
                        I.get(new Result.IKey(i, "AMMO", t)),
                        GRB.LESS_EQUAL,
                        ou.maxAmmoKg,
                        "CAP_AMMO_{i" + i + "_t" + t + "}");
            }
        }
    }

    /**
     * FSC capacity constraints (in CCL units).
     *
     * @throws GRBException if an error occurs
     */
    private void addFscCapacityConstraints() throws GRBException {
        for (FSC w : fscs) {
            for (int t = 1; t <= this.data.timeHorizon; t++) {
                GRBLinExpr lhs = new GRBLinExpr();

                for (CCLPackage c : this.cclTypes) {
                    for (String o : this.ouTypes) {
                        lhs.addTerm(1.0, S.get(new Result.SKey(w.FSCname, c.type, o, t)));
                    }
                }

                model.addConstr(
                        lhs,
                        GRB.LESS_EQUAL,
                        w.maxStorageCapCcls,
                        "FSC_CAP_{w" + w.FSCname + "_t" + t + "}");
            }
        }
    }

    /**
     * Optimize the model with Gurobi.
     *
     * @throws GRBException if an error occurs
     */
    public void solve() throws GRBException {
        model.optimize();
    }

    /**
     * Dispose the Gurobi model.
     */
    public void dispose() {
        model.dispose();
    }

    /**
     * Gets the Gurobi model.
     *
     * @return the Gurobi model
     */
    public GRBModel getModel() {
        return model;
    }

    /**
     * Solves 1 or more instances and returns a Result per instance.
     * One shared GRBEnv is used for efficiency.
     */
    public static List<Result> solveInstances(List<Instance> instances) {
        if (instances == null || instances.isEmpty()) {
            return Collections.emptyList();
        }

        List<Result> results = new ArrayList<>();
        GRBEnv env = null;

        try {
            env = new GRBEnv(true);
            env.set("logFile", "gurobi_batch.log");
            env.start();

            for (int idx = 0; idx < instances.size(); idx++) {
                int total = instances.size();
                int percent = (int) Math.round(100.0 * (idx + 1) / total);

                System.out.printf(
                        "\rProgress: %3d%% (%d / %d instances)",
                        percent, idx + 1, total);
                System.out.flush();

                Instance inst = instances.get(idx);
                String instanceName = "Instance " + (idx + 1);

                CapacitatedResupplyMILP milp = null;
                try {
                    milp = new CapacitatedResupplyMILP(inst, env, false);
                    milp.solve();

                    int status = milp.model.get(GRB.IntAttr.Status);
                    boolean optimal = (status == GRB.Status.OPTIMAL);
                    Double objVal = optimal ? milp.model.get(GRB.DoubleAttr.ObjVal) : null;

                    // Trucks: M and K_FSC_1..K_FSC_10 (missing -> 0)
                    int trucksAtMsc = (milp.mscTruckVar == null) ? 0 : (int) Math.round(milp.mscTruckVar.get(GRB.DoubleAttr.X));

                    int[] trucksAtFsc = new int[10];
                    for (int f = 1; f <= 10; f++) {
                        String fscName = "FSC_" + f;
                        GRBVar kv = milp.fscTruckVars.get(fscName);
                        trucksAtFsc[f - 1] = (kv == null) ? 0 : (int) Math.round(kv.get(GRB.DoubleAttr.X));
                    }

                    // For each FSC_1..FSC_10, list OUs actually supplied (x>0 anywhere)
                    List<Set<String>> suppliedSets = new ArrayList<>();
                    for (int i = 0; i < 10; i++)
                        suppliedSets.add(new HashSet<>());

                    for (Map.Entry<Result.XKey, GRBVar> e : milp.x.entrySet()) {
                        double val = e.getValue().get(GRB.DoubleAttr.X);
                        if (val <= EPS)
                            continue;

                        Result.XKey key = e.getKey();
                        String w = key.fsc(); // FSC name
                        String ou = key.ou(); // OU name

                        // Map FSC_1..FSC_10 -> index 0..9
                        if (w != null && w.startsWith("FSC_")) {
                            try {
                                int num = Integer.parseInt(w.substring(4));
                                if (num >= 1 && num <= 10) {
                                    suppliedSets.get(num - 1).add(ou);
                                }
                            } catch (NumberFormatException ignored) {
                                // not in FSC_1..FSC_10 format
                            }
                        }
                    }

                    List<List<String>> ouLists = new ArrayList<>();
                    for (int i = 0; i < 10; i++) {
                        List<String> list = new ArrayList<>(suppliedSets.get(i));
                        Collections.sort(list);
                        ouLists.add(list);
                    }

                    if (objVal != null) {
                        results.add(makeNewResult(instanceName, status, optimal, objVal, trucksAtMsc, trucksAtFsc,
                                ouLists, milp));
                    }

                } catch (Exception e) {
                    throw new RuntimeException("Failed solving " + instanceName + ": " + e.getMessage(), e);
                } finally {
                    if (milp != null) {
                        try {
                            milp.dispose();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            return results;

        } catch (Exception e) {
            throw new RuntimeException("Failed in solveInstances(): " + e.getMessage(), e);
        } finally {
            if (env != null) {
                try {
                    env.dispose();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Extracts variable values from the solved MILP and creates a Result object. 
     * Collects the optimal truck allocation and decision variable values from the Gurobi model.
     * @param instanceName  name of the instance
     * @param status        the Gurobi optimization status
     * @param optimal       true if the solution is optimal, false otherwise    
     * @param objVal        the objective function value (total trucks)
     * @param trucksAtMsc   the number of trucks stationed at the MSC
     * @param trucksAtFsc   array of trucks stationed at each FSC
     * @param ouLists       for each FSC, a list of operating units it supplies
     * @param milp          the solved MILP model containing the decision variables
     * @return a Result object containing all solution details
     * @throws GRBException if an error occurs accessing Gurobi variables
     */
    public static Result makeNewResult(String instanceName, int status, boolean optimal, double objVal, int trucksAtMsc,
            int[] trucksAtFsc, List<List<String>> ouLists, CapacitatedResupplyMILP milp) throws GRBException {
        int mValue = (milp.mscTruckVar == null) ? 0 : (int) Math.round(milp.mscTruckVar.get(GRB.DoubleAttr.X));

        Map<String, Integer> kValue = new HashMap<>();
        for (Map.Entry<String, GRBVar> e : milp.fscTruckVars.entrySet()) {
            kValue.put(e.getKey(), (int) Math.round(e.getValue().get(GRB.DoubleAttr.X)));
        }

        Map<Result.XKey, Integer> xValue = new HashMap<>();
        for (Map.Entry<Result.XKey, GRBVar> e : milp.x.entrySet()) {
            int v = (int) Math.round(e.getValue().get(GRB.DoubleAttr.X));
            if (v != 0)
                xValue.put(e.getKey(), v);
        }

        Map<Result.YKey, Integer> yValue = new HashMap<>();
        for (Map.Entry<Result.YKey, GRBVar> e : milp.y.entrySet()) {
            int v = (int) Math.round(e.getValue().get(GRB.DoubleAttr.X));
            if (v != 0)
                yValue.put(e.getKey(), v);
        }

        Map<Result.ZKey, Integer> zValue = new HashMap<>();
        for (Map.Entry<Result.ZKey, GRBVar> e : milp.z.entrySet()) {
            int v = (int) Math.round(e.getValue().get(GRB.DoubleAttr.X));
            if (v != 0)
                zValue.put(e.getKey(), v);
        }

        Map<Result.IKey, Double> iValue = new HashMap<>();
        for (Map.Entry<Result.IKey, GRBVar> e : milp.I.entrySet()) {
            double v = e.getValue().get(GRB.DoubleAttr.X);
            // keep all or filter tiny:
            if (Math.abs(v) > EPS)
                iValue.put(e.getKey(), v);
        }

        Map<Result.SKey, Integer> sValue = new HashMap<>();
        for (Map.Entry<Result.SKey, GRBVar> e : milp.S.entrySet()) {
            int v = (int) Math.round(e.getValue().get(GRB.DoubleAttr.X));
            if (v != 0)
                sValue.put(e.getKey(), v);
        }

        return new Result(instanceName, status, optimal, objVal, trucksAtMsc, trucksAtFsc, ouLists, mValue, kValue,
                xValue, yValue, zValue, iValue, sValue);
    }

    /**
     * FSC dispatch feasibility constraints.
     *
     * Ensures that on every day t an FSC can only dispatch CCL packages it actually
     * holds at the START of that day, before the MSC replenishment (y) arrives.
     *
     * Without this constraint the MILP balance equation
     *   S[w,c,o,t+1] = S[w,c,o,t] − x[w,i,c,t] + y[w,c,o,t]
     * allows x > S[w,c,o,t] (a negative intermediate inventory) as long as the
     * same-day y delivery covers the shortfall.  That is physically infeasible.
     *
     * Constraint added: S[w,c,o,t] >= Σ_{i: source=w, ouType=o} x[w,i,c,t]
     *
     * @throws GRBException if an error occurs
     */
    private void addFscDispatchFeasibilityConstraints() throws GRBException {
        for (int t = 1; t <= this.data.timeHorizon; t++) {
            for (FSC w : this.fscs) {
                for (CCLPackage c : this.cclTypes) {
                    for (String o : this.ouTypes) {
                        if (o.equals("VUST")) continue;

                        GRBLinExpr dispatchSum = new GRBLinExpr();
                        for (OperatingUnit i : this.ous) {
                            if (!i.source.equals(w.FSCname)) continue;
                            if (!i.ouType.equals(o)) continue;
                            GRBVar xv = x.get(new Result.XKey(w.FSCname, i.operatingUnitName, c.type, t));
                            if (xv != null) dispatchSum.addTerm(1.0, xv);
                        }

                        model.addConstr(
                                S.get(new Result.SKey(w.FSCname, c.type, o, t)),
                                GRB.GREATER_EQUAL,
                                dispatchSum,
                                "FSC_DISP_{w" + w.FSCname + "_c" + c.type + "_o" + o + "_t" + t + "}");
                    }
                }
            }
        }
    }

    /**
     * Helper method that maked demand time dependent (for perfect hindsight evaluation)
     * @param ou    the operating unit
     * @param p     the product type
     * @param t     the day
     * @return demand of product p (kg), at day t, at operating unit ou
     */
    private double demandAt(OperatingUnit ou, String p, int t) {
        int idx = t - 1; 

        switch (p) {
            case "FW" -> {
                if (ou.stochasticFoodWaterKg != null)
                    return ou.stochasticFoodWaterKg[idx];
                return ou.dailyFoodWaterKg;
            }
            case "FUEL" -> {
                if (ou.stochasticFuelKg != null)
                    return ou.stochasticFuelKg[idx];
                return ou.dailyFuelKg;
            }
            case "AMMO" -> {
                if (ou.stochasticAmmoKg != null)
                    return ou.stochasticAmmoKg[idx];
                return ou.dailyAmmoKg;
            }
            default -> throw new IllegalArgumentException("Unknown product: " + p);
        }
    }
}
