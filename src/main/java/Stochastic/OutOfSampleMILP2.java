package Stochastic;

import DataUtils.InstanceCreator;
import com.gurobi.gurobi.*;
import Objects.*;

import java.util.*;

/**
 * MILP for the Capacitated Resupply Problem
 */
public class OutOfSampleMILP2 {
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

    private final Map<IKey, GRBConstr> inventoryBalanceConstraints = new HashMap<>();
    private final Map<SKey, GRBConstr> FSCInventoryBalanceConstraints = new HashMap<>();

    private Map<IKey, Double> currentInventory = new HashMap<>(); // new
    private Map<SKey, Double> currentFSCInventory = new HashMap<>(); // new
    private boolean stockout = false; // new

    // Number of trucks stationed at MSC
    private double M;
    // Number of trucks stationed at FSC w
    private Map<String, Double> K = new HashMap<>();

    // Record keys for map indexing
    private record Arc(String w, String i) {
    }

    private record XKey(String w, String i, int c) {
    }

    private record YKey(String w, int c, String o) {
    }

    private record ZKey(int c) {
    }

    private record IKey(String i, String p) {
    }

    private record SKey(String w, int c, String o) {
    }

    private record DemandKey(String i, String p) {
    }

    private record PWLData(double[] vBreaks, double[] qBreaks) {
    }

    /**
     * Build the MILP model (derive sets, create variables, add constraints and set
     * objective)
     */
    public OutOfSampleMILP2(Instance data, GRBEnv env, double m, Map<String, Double> k, boolean verbose)
            throws GRBException {
        this.data = data;
        this.fscs = this.data.FSCs;
        this.ous = this.data.operatingUnits;
        this.products = this.data.products;
        this.cclTypes = this.data.cclTypes;
        this.ouTypes = this.data.ouTypes;
        this.M = m;
        this.K = k;

        precomputePWLFunctions();
        buildArcs();
        setInitialInventories();
        stockout = false;

        env.set(GRB.IntParam.OutputFlag, verbose ? 1 : 0);
        this.model = new GRBModel(env);

        buildVariables();
        buildConstraints();
        buildObjective();

        model.update();
    }

    public void runRollingHorizon() throws GRBException {

        Map<Integer, Map<DemandKey, Double>> realizedDemandsByDay = generateDemand();
        for (int day = 1; day < data.timeHorizon; day++) {

            // System.out.println("=== DAY " + day + " ===");

            realizedDemands(realizedDemandsByDay, day);

            updateFSCInventoryBalanceConstraintRHS();
            updateInventoryBalanceConstraintRHS();

            model.optimize();
            // printTruckUsage();

            // System.out.println(model.get(GRB.DoubleAttr.ObjVal));

            if (model.get(GRB.IntAttr.Status) != GRB.Status.OPTIMAL) {
                System.out.println("Model not optimal on day " + day);
                break;
            }

            // Retrieve inventories after sending out the trucks
            Map<IKey, Double> newInventory = new HashMap<>();
            for (Map.Entry<IKey, GRBVar> e : I.entrySet()) {
                newInventory.put(e.getKey(), e.getValue().get(GRB.DoubleAttr.X));
            }
            currentInventory = newInventory;

            Map<SKey, Double> newFSCInventory = new HashMap<>();
            for (Map.Entry<SKey, GRBVar> e : S.entrySet()) {
                newFSCInventory.put(e.getKey(), e.getValue().get(GRB.DoubleAttr.X));
            }

            currentFSCInventory = newFSCInventory;

            model.update();
        }
        // System.out.println("=== DAY " + 10 + " ===");
        realizedDemands(realizedDemandsByDay, data.timeHorizon);

    }

    private void printTruckUsage() throws GRBException {

        double trucksMSC = 0;

        for (GRBVar var : y.values())
            trucksMSC += var.get(GRB.DoubleAttr.X);

        for (GRBVar var : z.values())
            trucksMSC += var.get(GRB.DoubleAttr.X);

        System.out.println("MSC trucks used: " + trucksMSC + " / " + M);

        for (FSC fsc : fscs) {

            double used = 0;

            for (Arc a : arcs) {
                if (!a.w().equals(fsc.FSCname))
                    continue;

                for (CCLpackage c : cclTypes) {
                    used += x.get(new XKey(fsc.FSCname, a.i(), c.type))
                            .get(GRB.DoubleAttr.X);
                }
            }

            System.out.println("FSC " + fsc.FSCname + ": " + used + " / " + K.get(fsc.FSCname));
        }
    }

    public void realizedDemands(Map<Integer, Map<DemandKey, Double>> realizedDemandsByDay, int day) {
        Map<IKey, Double> newInventory = new HashMap<>();
        Map<DemandKey, Double> dailyDemands = realizedDemandsByDay.get(day);
        for (Map.Entry<IKey, Double> e : currentInventory.entrySet()) {
            IKey key = e.getKey();
            DemandKey demandKey = new DemandKey(key.i(), key.p());
            double inv = e.getValue();
            double newInv = inv - dailyDemands.get(demandKey);
            if (newInv < 1e-6) {
                // System.out.println("Stockout at OU " + key.i() + " product " + key.p() + "
                // time " + day + " of " + (-newInv));
                newInv = 0;
                stockout = true;
            }
            newInventory.put(key, newInv);
        }
        currentInventory = newInventory;
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
        double[] vBreaks = new double[] {
                0.0, 0.005, 0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.08, 0.1, 0.15, 0.2, 0.25, 0.3, 0.4, 0.5, 0.6,
                0.7, 0.8, 0.9
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
        double prob = Math.pow(1 - p, n);
        double cdf = prob;

        for (int x = 0; x <= n; x++) {

            // Compute P(x) from P(x-1)
            if (x > 0) {
                prob = prob * (n - x + 1) / x * p / (1 - p);
                cdf += prob;
            }
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

    private void setInitialInventories() {
        for (OperatingUnit ou : ous) {
            currentInventory.put(new IKey(ou.operatingUnitName, "FW"), ou.maxFoodWaterKg);
            currentInventory.put(new IKey(ou.operatingUnitName, "FUEL"), ou.maxFuelKg);
            currentInventory.put(new IKey(ou.operatingUnitName, "AMMO"), ou.maxAmmoKg);
        }
        for (FSC w : fscs) {
            for (CCLpackage c : this.cclTypes) {
                for (String o : this.ouTypes) {
                    if (o.equals("VUST")) {
                        continue;
                    }
                    currentFSCInventory.put(new SKey(w.FSCname, c.type, o),
                            (double) w.initialStorageLevels.get(o)[c.type - 1]);
                }
            }
        }

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
                XKey key = new XKey(w, i, c.type);
                x.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER,
                        "x_{" + "w" + w + "_i" + i + "_c" + c.type + "}"));
            }
        }

        // y[w, c, o, t] : MSC -> FSC deliveries
        for (FSC fsc : fscs) {
            for (CCLpackage c : this.cclTypes) {
                for (String o : this.ouTypes) {
                    YKey key = new YKey(fsc.FSCname, c.type, o);
                    y.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER,
                            "y_{" + "w" + fsc.FSCname + "_c" + c.type + "_o" + o + "}"));
                }
            }
        }

        // z[c, t] : MSC -> Vust deliveries
        for (CCLpackage c : this.cclTypes) {
            ZKey key = new ZKey(c.type);
            z.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER,
                    "z_{" + "c" + c + "}"));
        }

        // I[i, p]: OU inventory (kg)
        for (OperatingUnit ou : ous) {
            String i = ou.operatingUnitName;
            for (String p : this.products) {
                IKey key = new IKey(i, p);
                I.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS,
                        "I_{" + "i" + i + "_p" + p + "}"));
            }
        }

        // S[w, c, o, t] : FSC inventory (#CCL)
        for (FSC fsc : fscs) {
            for (CCLpackage c : this.cclTypes) {
                for (String o : this.ouTypes) {
                    SKey key = new SKey(fsc.FSCname, c.type, o);
                    S.put(key, this.model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER,
                            "S_{" + "w" + fsc.FSCname + "_c" + c + "_o" + o + "}"));
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
                    "q_" + p);
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

        double reward = 0.001;

        for (GRBVar var : x.values()) {
            obj.addTerm(-reward, var);
        }

        for (GRBVar var : y.values())
            obj.addTerm(-reward, var);

        for (GRBVar var : z.values())
            obj.addTerm(-reward, var);

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

        // Inventory balance constraints
        addFscInventoryBalanceConstraints();
        addOuInventoryBalanceConstraints();

        // Service-level and capacity constraints
        addServiceLevelConstraints();
        addOuCapacityConstraints();
        addFscCapacityConstraints();
    }

    /**
     * FSC truck constraint (one CCL per truck per day)
     *
     * @throws GRBException if an error occurs
     */
    private void addTruckConstraintsFsc() throws GRBException {
        for (FSC w : fscs) {
            GRBLinExpr lhs = new GRBLinExpr();

            // Sum LHS
            for (Arc a : arcs) {
                if (!a.w().equals(w.FSCname)) {
                    continue;
                }
                for (CCLpackage c : cclTypes) {
                    GRBVar var = x.get(new XKey(w.FSCname, a.i(), c.type));
                    lhs.addTerm(1.0, var);
                }
            }

            model.addConstr(
                    lhs,
                    GRB.LESS_EQUAL,
                    K.get(w.FSCname),
                    "TRUCK_FSC_{w" + w.FSCname + "}");
        }
    }

    /**
     * MSC truck constraint (one CCL per truck per day)
     *
     * @throws GRBException if an error occurs
     */
    private void addTruckConstraintsMsc() throws GRBException {
        GRBLinExpr lhs = new GRBLinExpr();

        for (FSC w : fscs) {
            for (CCLpackage c : cclTypes) {
                for (String o : this.ouTypes) {
                    GRBVar var = y.get(new YKey(w.FSCname, c.type, o));
                    lhs.addTerm(1.0, var);
                }
            }
        }

        for (CCLpackage c : this.cclTypes) {
            lhs.addTerm(1.0, z.get(new ZKey(c.type)));
        }

        model.addConstr(lhs, GRB.LESS_EQUAL, M, "TRUCK_MSC");
    }

    /**
     * FSC inventory balance constraints.
     *
     * @throws GRBException if an error occurs
     */
    private void addFscInventoryBalanceConstraints() throws GRBException {
        for (FSC w : this.fscs) {
            for (CCLpackage c : this.cclTypes) {
                for (String o : this.ouTypes) {
                    if (o.equals("VUST")) {
                        continue;
                    }
                    GRBLinExpr rhs = new GRBLinExpr();

                    rhs.addConstant(currentFSCInventory.get(new SKey(w.FSCname, c.type, o)));

                    GRBLinExpr lhs = new GRBLinExpr();

                    lhs.addTerm(1.0, S.get(new SKey(w.FSCname, c.type, o)));

                    List<OperatingUnit> ousOfType = new ArrayList<>();
                    for (OperatingUnit ou : this.ous) {
                        if (ou.ouType.equals(o)) {
                            ousOfType.add(ou);
                        }
                    }

                    for (OperatingUnit i : ousOfType) {
                        if (i.source.equals(w.FSCname)) {
                            XKey xk = new XKey(w.FSCname, i.operatingUnitName, c.type);
                            GRBVar xv = x.get(xk);
                            lhs.addTerm(1.0, xv);
                        }
                    }

                    lhs.addTerm(-1.0, y.get(new YKey(w.FSCname, c.type, o)));

                    GRBConstr constr = model.addConstr(
                            lhs,
                            GRB.EQUAL,
                            rhs,
                            "FSC_BAL_{w" + w.FSCname + "_c" + c + "_o" + o + "}");

                    FSCInventoryBalanceConstraints.put(new SKey(w.FSCname, c.type, o), constr);
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
        for (OperatingUnit ou : this.ous) {
            String i = ou.operatingUnitName;

            for (String p : this.products) {
                GRBLinExpr rhs = new GRBLinExpr();

                // Start from current start-of-day inventory
                rhs.addConstant(currentInventory.get(new IKey(i, p)));

                GRBLinExpr lhs = new GRBLinExpr();

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
                        String w = ou.source; // the only FSC that can send to this OU
                        GRBVar xv = x.get(new XKey(w, i, c.type));
                        lhs.addTerm(-content, xv);
                    } else {
                        // VUST gets deliveries from MSC via z
                        lhs.addTerm(-content, z.get(new ZKey(c.type)));
                    }
                }

                lhs.addTerm(1.0, I.get(new IKey(i, p)));

                GRBConstr constr = model.addConstr(
                        lhs,
                        GRB.EQUAL,
                        rhs,
                        "OU_BAL_{-" + i + "_p" + p + "}");

                inventoryBalanceConstraints.put(new IKey(i, p), constr);
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
                    "PWL_" + p);
        }

        for (OperatingUnit ou : ous) {
            String i = ou.operatingUnitName;

            GRBLinExpr rhsFW = new GRBLinExpr();
            rhsFW.addConstant(1.2 * ou.dailyFoodWaterKg);
            rhsFW.addTerm(-0.4 * ou.dailyFoodWaterKg, e);
            model.addConstr(
                    I.get(new IKey(i, "FW")),
                    GRB.GREATER_EQUAL,
                    rhsFW,
                    "SERVICE_FW_{i" + i + "}");

            // PWLData pwlFUEL = pwlByProduct.get("FUEL");
            GRBVar qVarFUEL = qProduct.get("FUEL");
            GRBLinExpr rhsFUEL = new GRBLinExpr();
            rhsFUEL.addTerm(ou.dailyFuelKg, qVarFUEL);

            model.addConstr(
                    I.get(new IKey(i, "FUEL")),
                    GRB.GREATER_EQUAL,
                    rhsFUEL,
                    "SERVICE_FUEL_{i" + i + "}");

            // PWLData pwlAMMO = pwlByProduct.get("AMMO");
            GRBVar qVarAMMO = qProduct.get("AMMO");
            GRBLinExpr rhsAMMO = new GRBLinExpr();
            rhsAMMO.addTerm(ou.dailyAmmoKg, qVarAMMO);

            model.addConstr(
                    I.get(new IKey(i, "AMMO")),
                    GRB.GREATER_EQUAL,
                    rhsAMMO,
                    "SERVICE_AMMO_{" + i + "}");
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

            model.addConstr(
                    I.get(new IKey(i, "FW")),
                    GRB.LESS_EQUAL,
                    ou.maxFoodWaterKg,
                    "CAP_FW_{i" + i + "}");

            model.addConstr(
                    I.get(new IKey(i, "FUEL")),
                    GRB.LESS_EQUAL,
                    ou.maxFuelKg,
                    "CAP_FUEL_{i" + i + "}");

            model.addConstr(
                    I.get(new IKey(i, "AMMO")),
                    GRB.LESS_EQUAL,
                    ou.maxAmmoKg,
                    "CAP_AMMO_{i" + i + "}");
        }
    }

    /**
     * FSC capacity constraints (in CCL units).
     *
     * @throws GRBException if an error occurs
     */
    private void addFscCapacityConstraints() throws GRBException {
        for (FSC w : fscs) {
            GRBLinExpr lhs = new GRBLinExpr();

            for (CCLpackage c : this.cclTypes) {
                for (String o : this.ouTypes) {
                    lhs.addTerm(1.0, S.get(new SKey(w.FSCname, c.type, o)));
                }
            }

            model.addConstr(
                    lhs,
                    GRB.LESS_EQUAL,
                    w.maxStorageCapCcls,
                    "FSC_CAP_{w" + w.FSCname + "}");
        }
    }

    public void updateInventoryBalanceConstraintRHS() throws GRBException {
        for (Map.Entry<IKey, Double> e : currentInventory.entrySet()) {
            IKey key = e.getKey();
            double inv = e.getValue();
            inventoryBalanceConstraints.get(key).set(GRB.DoubleAttr.RHS, inv);
        }
        model.update();
    }

    public void updateFSCInventoryBalanceConstraintRHS() throws GRBException {
        for (Map.Entry<SKey, Double> e : currentFSCInventory.entrySet()) {
            SKey key = e.getKey();
            double inv = e.getValue();
            if (key.o().equals("VUST")) {
                continue;
            }
            FSCInventoryBalanceConstraints.get(key).set(GRB.DoubleAttr.RHS, inv);
        }
        model.update();
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

    private Map<Integer, Map<DemandKey, Double>> generateDemand() {
        Map<Integer, Map<DemandKey, Double>> realizedDemands = new HashMap<>();

        for (int t = 1; t <= data.timeHorizon; t++) {
            Map<DemandKey, Double> dailyDemands = new HashMap<>();
            Sampling rand = new Sampling();

            for (OperatingUnit ou : ous) {
                // Generate random demand
                double dFW = rand.uniform() * ou.dailyFoodWaterKg;
                dailyDemands.put(new DemandKey(ou.operatingUnitName, "FW"), dFW);

                double dFUEL = rand.binomial() * ou.dailyFuelKg;
                dailyDemands.put(new DemandKey(ou.operatingUnitName, "FUEL"), dFUEL);

                double dAMMO = rand.triangular() * ou.dailyAmmoKg;
                dailyDemands.put(new DemandKey(ou.operatingUnitName, "AMMO"), dAMMO);
            }

            realizedDemands.put(t, dailyDemands);
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
        int nrStockouts = 0;

        try {
            env = new GRBEnv(true);
            env.set("logFile", "gurobi_batch.log");
            env.start();

            for (int idx = 0; idx < 1000; idx++) {
                int total = 1000;
                int percent = (int) Math.round(100.0 * (idx + 1) / total);

                Instance inst = instances.get(0);
                String instanceName = "Instance " + (idx + 1);

                OutOfSampleMILP2 milp = null;
                try {
                    milp = new OutOfSampleMILP2(inst, env, m, k, false);
                    milp.runRollingHorizon();
                    if (milp.stockout) {
                        nrStockouts++;
                    }
                    System.out.printf(
                            "\rProgress: %3d%% (%d / %d instances) | Stockouts so far: %d",
                            percent, idx + 1, total, nrStockouts);

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
            System.out.flush();

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

    public static void main(String[] args) {
        OutOfSampleMILP2.solveInstances(InstanceCreator.createFDInstance(), 79.0, Map.of("FSC_1", 48.0, "FSC_2", 35.0));
    }

}