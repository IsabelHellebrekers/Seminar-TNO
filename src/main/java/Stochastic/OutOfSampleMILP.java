package Stochastic;

import DataUtils.InstanceCreator;
import com.gurobi.gurobi.*;
import Objects.*;

import java.util.*;

/**
 * MILP for the Capacitated Resupply Problem
 */
public class OutOfSampleMILP {
    private final Instance data;
    private final GRBModel model;


    // Lists of all FSCs, arcs, OUs, products, CCL types, OU Types
    private final List<FSC> fscs;
    private final List<Arc> arcs = new ArrayList<>();
    private final List<OperatingUnit> ous;
    private final List<String> products;
    private final List<CCLpackage> cclTypes; // index based
    private final List<String> ouTypes;

    // Hashmaps to easy access the GRBVariables
    private final Map<XKey, GRBVar> x = new HashMap<>();
    private final Map<YKey, GRBVar> y = new HashMap<>();
    private final Map<ZKey, GRBVar> z = new HashMap<>();
    private final Map<IKey, GRBVar> I = new HashMap<>();
    private final Map<SKey, GRBVar> S = new HashMap<>();

    private GRBVar e;

    private final Map<String, PWLData> pwlByProduct = new HashMap<>();
    private final Map<String, GRBVar> qProduct = new HashMap<>();
    private final Map<DemandKey, GRBConstr> inventoryBalanceConstraints = new HashMap<>();

    private final Map<DemandKey, Double> demand = new HashMap<>();

    // Number of trucks stationed at MSC
    private double M;
    // Number of trucks stationed at FSC w
    private Map<String, Double> K = new HashMap<>();

    // Record keys for map indexing
    private record Arc(String w, String i) {
    }

    private record XKey(String w, String i, int c, int t) {
    }

    private record YKey(String w, int c, String o, int t) {
    }

    private record ZKey(int c, int t) {
    }

    private record IKey(String i, String p, int t) {
    }

    private record SKey(String w, int c, String o, int t) {
    }

    private record DemandKey (String i, String p, int t) {
    }

    private record PWLData(double[] vBreaks, double[] qBreaks) {
    }

    /**
     * Build the MILP model (derive sets, create variables, add constraints and set objective)
     */
    public OutOfSampleMILP(Instance data, GRBEnv env, double m, Map<String, Double> k, boolean verbose) throws GRBException {
        this.data = data;
        this.fscs = this.data.FSCs;
        this.ous = this.data.operatingUnits;
        this.products = this.data.products;
        this.cclTypes = this.data.cclTypes;
        this.ouTypes = this.data.ouTypes;
        this.M = m;
        this.K = k;

        initializeDemandForecast();
        precomputePWLFunctions();
        buildArcs();

        env.set(GRB.IntParam.OutputFlag, verbose ? 1 : 0);
        this.model = new GRBModel(env);

        buildVariables();
        buildConstraints();
        buildObjective();

        model.update();
    }

    public void runRollingHorizon() throws GRBException {

        Map<DemandKey, Double> realizedDemandsByDay = generateDemand();
        for (int day = 1; day < data.timeHorizon; day++) {

            System.out.println("=== DAY " + day + " ===");

            model.optimize();

            System.out.println(model.get(GRB.DoubleAttr.ObjVal));

            hasStockout2(realizedDemandsByDay, day);

            if (model.get(GRB.IntAttr.Status) != GRB.Status.OPTIMAL) {
                System.out.println("Model not optimal on day " + day);
                break;
            }

            // Freeze everything before today
            freezePastDecisions(day);

            // Update realized demand for today (if available)
            for (Map.Entry<DemandKey, Double> e : realizedDemandsByDay.entrySet()) {
                if (e.getKey().t() == day) {
                    DemandKey key = e.getKey();
                    double realized = e.getValue();

                    double currentRHS = inventoryBalanceConstraints.get(key).get(GRB.DoubleAttr.RHS);
                    double updatedRHS = currentRHS + realized - demand.get(key);
                    inventoryBalanceConstraints.get(key).set(GRB.DoubleAttr.RHS, updatedRHS);
                }
            }

            model.update();
        }
        model.optimize();

        // Check whether there was any stockout
        boolean stockout = hasStockout(realizedDemandsByDay);
        System.out.println("Has stockout: " + stockout);
    }

    private void freezePastDecisions(int currentDay) throws GRBException {

        for (Map.Entry<XKey, GRBVar> e : x.entrySet()) {
            if (e.getKey().t() < currentDay) {
                double val = e.getValue().get(GRB.DoubleAttr.X);
                e.getValue().set(GRB.DoubleAttr.LB, val);
                e.getValue().set(GRB.DoubleAttr.UB, val);
            }
        }

        for (Map.Entry<YKey, GRBVar> e : y.entrySet()) {
            if (e.getKey().t() < currentDay) {
                double val = e.getValue().get(GRB.DoubleAttr.X);
                e.getValue().set(GRB.DoubleAttr.LB, val);
                e.getValue().set(GRB.DoubleAttr.UB, val);
            }
        }

        for (Map.Entry<ZKey, GRBVar> e : z.entrySet()) {
            if (e.getKey().t() < currentDay) {
                double val = e.getValue().get(GRB.DoubleAttr.X);
                e.getValue().set(GRB.DoubleAttr.LB, val);
                e.getValue().set(GRB.DoubleAttr.UB, val);
            }
        }

        for (Map.Entry<IKey, GRBVar> e : I.entrySet()) {
            if (e.getKey().t() < currentDay) {
                double val = e.getValue().get(GRB.DoubleAttr.X);
                e.getValue().set(GRB.DoubleAttr.LB, val);
                e.getValue().set(GRB.DoubleAttr.UB, val);
            }
        }

        for (Map.Entry<SKey, GRBVar> e : S.entrySet()) {
            if (e.getKey().t() < currentDay) {
                double val = e.getValue().get(GRB.DoubleAttr.X);
                e.getValue().set(GRB.DoubleAttr.LB, val);
                e.getValue().set(GRB.DoubleAttr.UB, val);
            }
        }
    }

    /**
     * Checks if any OU inventory drops below zero (stockout) after the rolling horizon.
     * Returns true if any stockout occurs.
     */
    public boolean hasStockout(Map<DemandKey, Double> realizedDemandsByDay) throws GRBException {
        for (OperatingUnit ou : ous) {
            String i = ou.operatingUnitName;
            for (String p : this.products) {
                for (int t = 1; t <= this.data.timeHorizon; t++) {
                    IKey ikey = new IKey(i, p, t);
                    DemandKey dkey = new DemandKey(i, p, t);
                    double d = realizedDemandsByDay.get(dkey);
                    double inv = I.get(ikey).get(GRB.DoubleAttr.X);
                    if (inv < d) {
                        //if (inv < -1e-6) {
                        System.out.println("Stockout at OU " + i + " product " + p + " time " + t);
                        return true;
                    }

                }
            }
        }
        return false;
    }

    public boolean hasStockout2(Map<DemandKey, Double> realizedDemandsByDay, int t) throws GRBException {
        for (OperatingUnit ou : ous) {
            String i = ou.operatingUnitName;
            for (String p : this.products) {
                IKey ikey = new IKey(i, p, t);
                DemandKey dkey = new DemandKey(i, p, t);
                double d = realizedDemandsByDay.get(dkey);
                double inv = I.get(ikey).get(GRB.DoubleAttr.X);
                if (inv < d) {
                    //if (inv < -1e-6) {
                    System.out.println("Stockout at OU " + i + " product " + p + " time " + t + " of " + (d - inv));
                    return true;
                }
            }
        }
        return false;
    }


    private void initializeDemandForecast() {

        for (OperatingUnit ou : ous) {
            String i = ou.operatingUnitName;

            for (int t = 1; t <= data.timeHorizon; t++) {

                demand.put(new DemandKey(i, "FW", t),
                        ou.dailyFoodWaterKg);

                demand.put(new DemandKey(i, "FUEL", t),
                        ou.dailyFuelKg);

                demand.put(new DemandKey(i, "AMMO", t),
                        ou.dailyAmmoKg);
            }
        }
    }

    private void precomputePWLFunctions() {

        pwlByProduct.put("FUEL", buildBinomialPWL());
        pwlByProduct.put("AMMO", buildTriangularPWL());

    }

    private PWLData buildTriangularPWL() {

        double a = 0.2;
        double m = 0.8;
        double b = 2.0;

        // breakpoints concentrated near v=0
        double[] vBreaks = new double[]{
                0.0, 0.005, 0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.08, 0.1, 0.15, 0.2
        };

        double[] qValues = new double[vBreaks.length];

        for (int k = 0; k < vBreaks.length; k++) {

            double v = vBreaks[k];
            double u = 1.0 - v;

            double Fm = (m - a) / (b - a);

            if (u <= Fm) {
                qValues[k] = a + Math.sqrt(u * (b - a) * (m - a));
            } else {
                qValues[k] = b - Math.sqrt((1 - u) * (b - a) * (b - m));
            }
        }

        return new PWLData(vBreaks, qValues);
    }

    private PWLData buildBinomialPWL() {

        int n = 25;
        double p = 0.4;

        List<Double> vList = new ArrayList<>();
        List<Double> qList = new ArrayList<>();

        // P(0)
        double prob = Math.pow(1-p, n);
        double cdf = prob;

        // store x = 0
        vList.add(1.0 - cdf);
        qList.add(0.0);

        for (int x = 1; x <= n; x++) {

            // Compute P(x) from P(x-1)
            prob = prob * (n - x) / (x + 1) * p / (1-p);
            cdf += prob;
            double v = 1.0 - cdf;

            vList.add(v);
            double k = x / 10.0;
            qList.add(k);
        }

        Collections.reverse(vList);
        Collections.reverse(qList);

        // Remove duplicates
        List<Double> cleanV = new ArrayList<>();
        List<Double> cleanQ = new ArrayList<>();

        double lastV = -1;

        for (int i = 0; i < vList.size(); i++) {
            double v = vList.get(i);

            if (i == 0 || v > lastV + 1e-5) {
                cleanV.add(v);
                cleanQ.add(qList.get(i));
                lastV = v;
            }
        }

        double[] vBreaks = cleanV.stream().mapToDouble(d -> d).toArray();
        double[] qBreaks = cleanQ.stream().mapToDouble(d -> d).toArray();

        return new PWLData(vBreaks, qBreaks);
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
        // x[w, i, c, t] : FSC -> OU deliveries
        for (Arc a : arcs) {
            String w = a.w();
            String i = a.i();
            for (CCLpackage c : this.cclTypes) {
                for (int t = 1; t <= this.data.timeHorizon; t++) {
                    XKey key = new XKey(w, i, c.type, t);
                    x.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER,
                            "x_{" + "w" + w + "_i" + i + "_c" + c + "_t" + t + "}"
                    ));
                }
            }
        }

        // y[w, c, o, t] : MSC -> FSC deliveries
        for (FSC fsc : fscs) {
            for (CCLpackage c : this.cclTypes) {
                for (String o : this.ouTypes) {
                    for (int t = 1; t <= this.data.timeHorizon; t++) {
                        YKey key = new YKey(fsc.FSCname, c.type, o, t);
                        y.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER,
                                "y_{" + "w" + fsc.FSCname + "_c" + c + "_o" + o + "_t" + t + "}"
                        ));
                    }
                }
            }
        }

        // z[c, t] : MSC -> Vust deliveries
        for (CCLpackage c : this.cclTypes) {
            for (int t = 1; t <= this.data.timeHorizon; t++) {
                ZKey key = new ZKey(c.type, t);
                z.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER,
                        "z_{" + "c" + c + "_t" + t + "}"
                ));
            }
        }

        // I[i, p, t]: OU inventory (kg)
        for (OperatingUnit ou : ous) {
            String i = ou.operatingUnitName;
            for (String p : this.products) {
                for (int t = 1; t <= this.data.timeHorizon; t++) {
                    IKey key = new IKey(i, p, t);
                    I.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS,
                            "I_{" + "i" + i + "_p" + p + "_t" + t + "}"
                    ));
                }
            }
        }

        // S[w, c, o, t] : FSC inventory (#CCL)
        for (FSC fsc : fscs) {
            for (CCLpackage c : this.cclTypes) {
                for (String o : this.ouTypes) {
                    for (int t = 1; t <= this.data.timeHorizon; t++) {
                        SKey key = new SKey(fsc.FSCname, c.type, o, t);
                        S.put(key, this.model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER,
                                "S_{" + "w" + fsc.FSCname + "_c" + c + "_o" + o + "_t" + t + "}"
                        ));
                    }
                }
            }
        }

        // e : highest percentile
        e = this.model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "w");

        // q_p : quantile per product
        for (String p : products) {
            GRBVar qVar = model.addVar(
                    0.0,
                    GRB.INFINITY,
                    0.0,
                    GRB.CONTINUOUS,
                    "q_" + p
            );
            qProduct.put(p, qVar);
        }
    }

    /**
     * Objective : minimize total number of trucks.
     *
     * @throws GRBException if an error occurs
     */
    private void buildObjective() throws GRBException {
        GRBLinExpr obj = new GRBLinExpr();

        obj.addTerm(1.0, this.e);

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
        addServiceLevelConstraints();
        addOuCapacityConstraints();
        addFscCapacityConstraints();
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
                    I.get(new IKey(i, "FW", 1)),
                    GRB.EQUAL,
                    ou.maxFoodWaterKg,
                    "OU_INIT_FW_" + i
            );

            // Fuel initial inventory
            model.addConstr(
                    I.get(new IKey(i, "FUEL", 1)),
                    GRB.EQUAL,
                    ou.maxFuelKg,
                    "OU_INIT_FUEL_" + i
            );

            // Ammunition initial inventory
            model.addConstr(
                    I.get(new IKey(i, "AMMO", 1)),
                    GRB.EQUAL,
                    ou.maxAmmoKg,
                    "OU_INIT_AMMO_" + i
            );
        }
    }

    /**
     * Set FSC starting inventory based on initial storage in the data.
     *
     * @throws GRBException if an error occurs
     */
    private void addFscInitialInventoryConstraints() throws GRBException {
        for (FSC w : fscs) {
            for (CCLpackage c : this.cclTypes) {
                for (String o : this.ouTypes) {
                    if (o.equals("VUST")) {
                        continue;
                    }
                    this.model.addConstr(
                            S.get(new SKey(w.FSCname, c.type, o, 1)),
                            GRB.EQUAL,
                            w.initialStorageLevels.get(o)[c.type - 1],
                            "FSC_INIT_{w" + w.FSCname + "_c" + c + "_o" + o + "}"
                    );

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
                    for (CCLpackage c : cclTypes) {
                        GRBVar var = x.get(new XKey(w.FSCname, a.i(), c.type, t));
                        lhs.addTerm(1.0, var);
                    }
                }

                model.addConstr(
                        lhs,
                        GRB.LESS_EQUAL,
                        K.get(w.FSCname),
                        "TRUCK_FSC_{w" + w.FSCname + "_t" + t + "}"
                );
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
                for (CCLpackage c : cclTypes) {
                    for (String o : this.ouTypes) {
                        GRBVar var = y.get(new YKey(w.FSCname, c.type, o, t));
                        lhs.addTerm(1.0, var);
                    }
                }
            }

            for (CCLpackage c : this.cclTypes) {
                lhs.addTerm(1.0, z.get(new ZKey(c.type, t)));
            }

            model.addConstr(lhs, GRB.LESS_EQUAL, M, "TRUCK_MSC_t" + t);
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
                for (CCLpackage c : this.cclTypes) {
                    for (String o : this.ouTypes) {
                        if (o.equals("VUST")) {
                            continue;
                        }
                        GRBLinExpr rhs = new GRBLinExpr();

                        rhs.addTerm(1.0, S.get(new SKey(w.FSCname, c.type, o, t)));

                        List<OperatingUnit> ousOfType = new ArrayList<>();
                        for (OperatingUnit ou : this.ous) {
                            if (ou.ouType.equals(o)) {
                                ousOfType.add(ou);
                            }
                        }

                        for (OperatingUnit i : ousOfType) {
                            if (i.source.equals(w.FSCname)) {
                                XKey xk = new XKey(w.FSCname, i.operatingUnitName, c.type, t);
                                GRBVar xv = x.get(xk);
                                rhs.addTerm(-1.0, xv);
                            }
                        }

                        rhs.addTerm(1.0, y.get(new YKey(w.FSCname, c.type, o, t)));

                        model.addConstr(
                                S.get(new SKey(w.FSCname, c.type, o, t + 1)),
                                GRB.EQUAL,
                                rhs,
                                "FSC_BAL_{w" + w.FSCname + "_c" + c + "_o" + o + "_t" + t + "}"
                        );
                    }
                }
            }
        }
    }

    /**
     * OU inventory balance constraints.
     *
     * @throws GRBException if an error occurs
     */
    private void addOuInventoryBalanceConstraints() throws GRBException {
        for (int t = 1; t <= this.data.timeHorizon - 1; t++) {
            for (OperatingUnit ou : this.ous) {
                String i = ou.operatingUnitName;

                for (String p : this.products) {
                    GRBLinExpr rhs = new GRBLinExpr();

                    // Start from current start-of-day inventory
                    rhs.addTerm(1.0, I.get(new IKey(i, p, t)));

                    // Subtract deterministic daily demand
                    double d = demand.get(new DemandKey(i, p, t));
                    rhs.addConstant(-d);

                    // Add deliveries shipped on day t (available at t + 1)
                    for (CCLpackage c : this.cclTypes) {

                        double content = switch (p) {
                            case "FW" -> c.foodWaterKg;
                            case "FUEL" -> c.fuelKg;
                            case "AMMO" -> c.ammoKg;
                            default -> throw new IllegalStateException("Unexpected value: " + p);
                        };

                        // Contributions from FSC shipments x
                        if (!i.equals("VUST")) {
                            String w = ou.source;  // the only FSC that can send to this OU
                            GRBVar xv = x.get(new XKey(w, i, c.type, t));
                            rhs.addTerm(content, xv);
                        } else {
                            // VUST gets deliveries from MSC via z
                            rhs.addTerm(content, z.get(new ZKey(c.type, t)));
                        }
                    }

                    GRBConstr constr = model.addConstr(
                            I.get(new IKey(i, p, t + 1)),
                            GRB.EQUAL,
                            rhs,
                            "OU_BAL_{-" + i + "_p" + p + "_t" + t + "}"
                    );

                    inventoryBalanceConstraints.put(new DemandKey(i, p, t), constr);
                }
            }
        }
    }

    /**
     * No stock-out constraints.
     *
     * @throws GRBException if an error occurs
     */
    private void addServiceLevelConstraints() throws GRBException {
        for (String p : List.of("FUEL", "AMMO")) {
            model.addGenConstrPWL(
                    e,
                    qProduct.get(p),
                    pwlByProduct.get(p).vBreaks(),
                    pwlByProduct.get(p).qBreaks(),
                    "PWL_" + p
            );
        }

        for (OperatingUnit ou : ous) {
            String i = ou.operatingUnitName;

            for (int t = 1; t <= this.data.timeHorizon; t++) {
                GRBLinExpr rhsFW = new GRBLinExpr();
                rhsFW.addConstant(1.2 * ou.dailyFoodWaterKg);
                rhsFW.addTerm(-0.4 * ou.dailyFoodWaterKg, e);
                model.addConstr(
                        I.get(new IKey(i, "FW", t)),
                        GRB.GREATER_EQUAL,
                        rhsFW,
                        "SERVICE_FW_{i" + i + "_t" + t + "}"
                );

                //PWLData pwlFUEL = pwlByProduct.get("FUEL");
                GRBVar qVarFUEL = qProduct.get("FUEL");
                GRBLinExpr rhsFUEL = new GRBLinExpr();
                rhsFUEL.addTerm(ou.dailyFuelKg, qVarFUEL);

                model.addConstr(
                        I.get(new IKey(i, "FUEL", t)),
                        GRB.GREATER_EQUAL,
                        rhsFUEL,
                        "SERVICE_FUEL_{i" + i + "_t" + t + "}"
                );

                //PWLData pwlAMMO = pwlByProduct.get("AMMO");
                GRBVar qVarAMMO = qProduct.get("AMMO");
                GRBLinExpr rhsAMMO = new GRBLinExpr();
                rhsAMMO.addTerm(ou.dailyAmmoKg, qVarAMMO);

                model.addConstr(
                        I.get(new IKey(i, "AMMO", t)),
                        GRB.GREATER_EQUAL,
                        rhsAMMO,
                        "SERVICE_AMMO_{" + i + "_t" + t + "}"
                );
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
                        I.get(new IKey(i, "FW", t)),
                        GRB.LESS_EQUAL,
                        ou.maxFoodWaterKg,
                        "CAP_FW_{i" + i + "_t" + t + "}"
                );

                model.addConstr(
                        I.get(new IKey(i, "FUEL", t)),
                        GRB.LESS_EQUAL,
                        ou.maxFuelKg,
                        "CAP_FUEL_{i" + i + "_t" + t + "}"
                );

                model.addConstr(
                        I.get(new IKey(i, "AMMO", t)),
                        GRB.LESS_EQUAL,
                        ou.maxAmmoKg,
                        "CAP_AMMO_{i" + i + "_t" + t + "}"
                );
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

                for (CCLpackage c : this.cclTypes) {
                    for (String o : this.ouTypes) {
                        lhs.addTerm(1.0, S.get(new SKey(w.FSCname, c.type, o, t)));
                    }
                }

                model.addConstr(
                        lhs,
                        GRB.LESS_EQUAL,
                        w.maxStorageCapCcls,
                        "FSC_CAP_{w" + w.FSCname + "_t" + t + "}"
                );
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

    private Map<DemandKey, Double> generateDemand() {
        Map<DemandKey, Double> realizedDemands = new HashMap<>();

        for (OperatingUnit ou : ous) {
            for (int t = 1; t <= data.timeHorizon; t++) {
                // Generate random demand
                Sampling rand = new Sampling(0);
                double dFW = rand.uniform() * ou.dailyFoodWaterKg;
                realizedDemands.put(new DemandKey(ou.operatingUnitName, "FW", t), dFW);

                double dFUEL = rand.binomial() * ou.dailyFuelKg;
                realizedDemands.put(new DemandKey(ou.operatingUnitName, "FUEL", t), dFUEL);

                double dAMMO = rand.triangular() * ou.dailyAmmoKg;
                realizedDemands.put(new DemandKey(ou.operatingUnitName, "AMMO", t), dAMMO);
            }
        }

        return realizedDemands;
    }

    /**
     * Solves 1 or more instances and returns a Result per instance.
     * One shared GRBEnv is used for efficiency.
     */
    public static List<Result> solveInstances(List<Instance> instances, double m, Map<String, Double> k) {
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
                        percent, idx + 1, total
                );
                System.out.flush();

                Instance inst = instances.get(idx);
                String instanceName = "Instance " + (idx + 1);

                OutOfSampleMILP milp = null;
                try {
                    milp = new OutOfSampleMILP(inst, env, m, k, false);
                    milp.runRollingHorizon();

                } catch (Exception e) {
                    throw new RuntimeException("Failed solving " + instanceName + ": " + e.getMessage(), e);
                } finally {
                    if (milp != null) {
                        try { milp.dispose(); } catch (Exception ignored) {}
                    }
                }
            }

            return results;

        } catch (Exception e) {
            throw new RuntimeException("Failed in solveInstances(): " + e.getMessage(), e);
        } finally {
            if (env != null) {
                try { env.dispose(); } catch (Exception ignored) {}
            }
        }
    }

    public static void main(String[] args) {
        OutOfSampleMILP.solveInstances(InstanceCreator.createFDInstance(), 200.0, Map.of("FSC_1", 200.0, "FSC_2", 200.0));
    }

}