package Stochastic;

import com.gurobi.gurobi.*;
import Objects.*;

import java.util.*;

/**
 * WindowMILP: identical spirit to OutOfSampleMILP, but only optimizes over a
 * window
 * [startDay .. endDay], with initial inventories provided as parameters
 * (state).
 *
 * Key differences vs OutOfSampleMILP:
 * - No whole-horizon model: only a window of days is modeled.
 * - Initial inventories are taken from simulation state (I0/S0), not set to
 * max/data.
 * - OU balance uses forecast demand = multiplier[p] * meanDemand.
 *
 * Objective remains: minimize e (epsilon).
 */
public class WindowMILP {

    private final Instance data;
    private final GRBModel model;

    private final List<FSC> fscs;
    private final List<OperatingUnit> ous;
    private final List<String> products;
    private final List<CCLpackage> cclTypes;
    private final List<String> ouTypes;

    private final List<Arc> arcs = new ArrayList<>();

    private final double M;
    private final Map<String, Double> K;

    private final int startDay;
    private final int endDay;
    private final int invEndDay;

    private final Map<String, Double> forecastMultiplier;

    // OU: I0[ouName][product] = kg
    private final Map<String, Map<String, Double>> I0;
    // FSC: S0[fscName][ouType][cType] = #CCL
    private final Map<String, Map<String, Map<Integer, Integer>>> S0;

    // ---- keys ----
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

    private record PWLData(double[] vBreaks, double[] qBreaks) {
    }

    // ---- vars ----
    private final Map<XKey, GRBVar> x = new HashMap<>();
    private final Map<YKey, GRBVar> y = new HashMap<>();
    private final Map<ZKey, GRBVar> z = new HashMap<>();
    private final Map<IKey, GRBVar> I = new HashMap<>();
    private final Map<SKey, GRBVar> S = new HashMap<>();

    private GRBVar e;
    private final Map<String, GRBVar> qProduct = new HashMap<>();
    private final Map<String, PWLData> pwlByProduct = new HashMap<>();

    public WindowMILP(
            Instance data,
            GRBEnv env,
            double M,
            Map<String, Double> K,
            int startDay,
            int windowLength,
            Map<String, Map<String, Double>> I0,
            Map<String, Map<String, Map<Integer, Integer>>> S0,
            Map<String, Double> forecastMultiplier,
            boolean verbose) throws GRBException {
        this.data = data;
        this.fscs = data.FSCs;
        this.ous = data.operatingUnits;
        this.products = data.products;
        this.cclTypes = data.cclTypes;
        this.ouTypes = data.ouTypes;

        this.M = M;
        this.K = K;

        this.startDay = startDay;
        this.endDay = Math.min(data.timeHorizon, startDay + windowLength - 1);

        this.invEndDay = Math.min(data.timeHorizon, this.endDay + 1);

        this.I0 = I0;
        this.S0 = S0;

        this.forecastMultiplier = new HashMap<>();
        for (String p : products)
            this.forecastMultiplier.put(p, 1.0);
        if (forecastMultiplier != null)
            this.forecastMultiplier.putAll(forecastMultiplier);

        env.set(GRB.IntParam.OutputFlag, verbose ? 1 : 0);
        this.model = new GRBModel(env);

        buildArcs();
        precomputePWLFunctions();

        buildVariables();
        buildConstraints();
        buildObjective();

        model.update();
    }

    public void solve() throws GRBException {
        model.optimize();
        if (model.get(GRB.IntAttr.SolCount) == 0) {
            model.computeIIS();
            model.write("iss_day_" + this.startDay + ".ilp");
            throw new GRBException("WindowMILP infeasible, IIS written to iss_day_" + this.startDay + ".ilp");
        }

        int status = model.get(GRB.IntAttr.Status);
        if (status != GRB.Status.OPTIMAL) {
            throw new GRBException("WindowMILP not optimal, status=" + status);
        }
    }

    public double getEpsilon() throws GRBException {
        return e.get(GRB.DoubleAttr.X);
    }

    // Day-1 decisions
    public int getX(String w, String i, int cType) throws GRBException {
        GRBVar v = x.get(new XKey(w, i, cType, startDay));
        return v == null ? 0 : (int) Math.round(v.get(GRB.DoubleAttr.X));
    }

    public int getY(String w, int cType, String o) throws GRBException {
        GRBVar v = y.get(new YKey(w, cType, o, startDay));
        return v == null ? 0 : (int) Math.round(v.get(GRB.DoubleAttr.X));
    }

    public int getZ(int cType) throws GRBException {
        GRBVar v = z.get(new ZKey(cType, startDay));
        return v == null ? 0 : (int) Math.round(v.get(GRB.DoubleAttr.X));
    }

    public void dispose() throws GRBException {
        model.dispose();
    }

    private void buildArcs() {
        for (OperatingUnit ou : ous) {
            if (ou.operatingUnitName.equals("VUST"))
                continue;
            arcs.add(new Arc(ou.source, ou.operatingUnitName));
        }
    }

    private void buildVariables() throws GRBException {

        // x[w,i,c,t] for t in [startDay..endDay]
        for (Arc a : arcs) {
            for (CCLpackage c : cclTypes) {
                for (int t = startDay; t <= endDay; t++) {
                    XKey key = new XKey(a.w(), a.i(), c.type, t);
                    x.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER,
                            "x_{w" + a.w() + "_i" + a.i() + "_c" + c.type + "_t" + t + "}"));
                }
            }
        }

        // y[w,c,o,t]
        for (FSC fsc : fscs) {
            for (CCLpackage c : cclTypes) {
                for (String o : ouTypes) {
                    for (int t = startDay; t <= endDay; t++) {
                        YKey key = new YKey(fsc.FSCname, c.type, o, t);
                        y.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER,
                                "y_{w" + fsc.FSCname + "_c" + c.type + "_o" + o + "_t" + t + "}"));
                    }
                }
            }
        }

        // z[c,t]
        for (CCLpackage c : cclTypes) {
            for (int t = startDay; t <= endDay; t++) {
                ZKey key = new ZKey(c.type, t);
                z.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER,
                        "z_{c" + c.type + "_t" + t + "}"));
            }
        }

        // I[i,p,t] for t in [startDay..invEndDay]
        for (OperatingUnit ou : ous) {
            for (String p : products) {
                for (int t = startDay; t <= invEndDay; t++) {
                    IKey key = new IKey(ou.operatingUnitName, p, t);
                    I.put(key, model.addVar(-GRB.INFINITY, GRB.INFINITY, 0.0, GRB.CONTINUOUS,
                            "I_{i" + ou.operatingUnitName + "_p" + p + "_t" + t + "}"));
                }
            }
        }

        // S[w,c,o,t] for t in [startDay..invEndDay], excluding VUST type
        for (FSC w : fscs) {
            for (CCLpackage c : cclTypes) {
                for (String o : ouTypes) {
                    if (o.equals("VUST"))
                        continue;
                    for (int t = startDay; t <= invEndDay; t++) {
                        SKey key = new SKey(w.FSCname, c.type, o, t);
                        S.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER,
                                "S_{w" + w.FSCname + "_c" + c.type + "_o" + o + "_t" + t + "}"));
                    }
                }
            }
        }

        // epsilon
        e = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "w");

        // q vars
        for (String p : products) {
            qProduct.put(p, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "q_" + p));
        }
    }

    // objective
    private void buildObjective() throws GRBException {
        GRBLinExpr obj = new GRBLinExpr();
        obj.addTerm(1.0, e);
        model.setObjective(obj, GRB.MINIMIZE);
    }

    // Constraints
    private void buildConstraints() throws GRBException {
        addTruckConstraintsFsc();
        addTruckConstraintsMsc();

        addInitialInventoryConstraintsFromState();

        addFscInventoryBalanceConstraints();
        addOuInventoryBalanceConstraints();

        addServiceLevelConstraints();
        addOuCapacityConstraints();
        addFscCapacityConstraints();

        addNoOverflowConstraints();
    }

    private void addInitialInventoryConstraintsFromState() throws GRBException {
        // OU initial inventories at startDay
        for (OperatingUnit ou : ous) {
            String i = ou.operatingUnitName;
            Map<String, Double> perP = I0.get(i);
            if (perP == null)
                throw new GRBException("Missing I0 for OU " + i);
            for (String p : products) {
                Double v = perP.get(p);
                if (v == null)
                    throw new GRBException("Missing I0 for OU " + i + " product " + p);
                model.addConstr(I.get(new IKey(i, p, startDay)), GRB.EQUAL, v,
                        "OU_INIT_{i" + i + "_p" + p + "}");
            }
        }

        // FSC initial inventories at startDay
        for (FSC w : fscs) {
            String wName = w.FSCname;
            Map<String, Map<Integer, Integer>> perType = S0.get(wName);
            if (perType == null)
                throw new GRBException("Missing S0 for FSC " + wName);

            for (String o : ouTypes) {
                if (o.equals("VUST"))
                    continue;
                Map<Integer, Integer> perC = perType.get(o);
                if (perC == null)
                    throw new GRBException("Missing S0 for FSC " + wName + " type " + o);

                for (CCLpackage c : cclTypes) {
                    Integer v = perC.get(c.type);
                    if (v == null)
                        throw new GRBException("Missing S0 for FSC " + wName + " type " + o + " c " + c.type);
                    model.addConstr(S.get(new SKey(wName, c.type, o, startDay)), GRB.EQUAL, v,
                            "FSC_INIT_{w" + wName + "_c" + c.type + "_o" + o + "}");
                }
            }
        }
    }

    private void addTruckConstraintsFsc() throws GRBException {
        for (FSC w : fscs) {
            for (int t = startDay; t <= endDay; t++) {
                GRBLinExpr lhs = new GRBLinExpr();
                for (Arc a : arcs) {
                    if (!a.w().equals(w.FSCname))
                        continue;
                    for (CCLpackage c : cclTypes) {
                        lhs.addTerm(1.0, x.get(new XKey(w.FSCname, a.i(), c.type, t)));
                    }
                }
                model.addConstr(lhs, GRB.LESS_EQUAL, K.get(w.FSCname),
                        "TRUCK_FSC_{w" + w.FSCname + "_t" + t + "}");
            }
        }
    }

    private void addTruckConstraintsMsc() throws GRBException {
        for (int t = startDay; t <= endDay; t++) {
            GRBLinExpr lhs = new GRBLinExpr();

            for (FSC w : fscs) {
                for (CCLpackage c : cclTypes) {
                    for (String o : ouTypes) {
                        lhs.addTerm(1.0, y.get(new YKey(w.FSCname, c.type, o, t)));
                    }
                }
            }
            for (CCLpackage c : cclTypes) {
                lhs.addTerm(1.0, z.get(new ZKey(c.type, t)));
            }

            model.addConstr(lhs, GRB.LESS_EQUAL, M, "TRUCK_MSC_t" + t);
        }
    }

    private void addFscInventoryBalanceConstraints() throws GRBException {
        for (int t = startDay; t <= endDay; t++) {
            if (t + 1 > invEndDay)
                break;

            for (FSC w : fscs) {
                for (CCLpackage c : cclTypes) {
                    for (String o : ouTypes) {
                        if (o.equals("VUST"))
                            continue;

                        GRBLinExpr rhs = new GRBLinExpr();
                        rhs.addTerm(1.0, S.get(new SKey(w.FSCname, c.type, o, t)));

                        // subtract outbound x to OUs of type o, sourced by this FSC
                        for (OperatingUnit ou : ous) {
                            if (ou.operatingUnitName.equals("VUST"))
                                continue;
                            if (!ou.source.equals(w.FSCname))
                                continue;
                            if (!ou.ouType.equals(o))
                                continue;

                            rhs.addTerm(-1.0, x.get(new XKey(w.FSCname, ou.operatingUnitName, c.type, t)));
                        }

                        // add inbound y
                        rhs.addTerm(1.0, y.get(new YKey(w.FSCname, c.type, o, t)));

                        model.addConstr(
                                S.get(new SKey(w.FSCname, c.type, o, t + 1)),
                                GRB.EQUAL,
                                rhs,
                                "FSC_BAL_{w" + w.FSCname + "_c" + c.type + "_o" + o + "_t" + t + "}");
                    }
                }
            }
        }
    }

    private void addOuInventoryBalanceConstraints() throws GRBException {
        for (int t = startDay; t <= endDay; t++) {
            if (t + 1 > invEndDay)
                break;

            for (OperatingUnit ou : ous) {
                String i = ou.operatingUnitName;

                for (String p : products) {
                    GRBLinExpr rhs = new GRBLinExpr();
                    rhs.addTerm(1.0, I.get(new IKey(i, p, t)));

                    // forecast demand = multiplier[p] * mean demand
                    double mult = forecastMultiplier.getOrDefault(p, 1.0);
                    double dem = mult * meanDemand(ou, p);
                    rhs.addConstant(-dem);

                    // deliveries shipped on day t arrive at t+1
                    for (CCLpackage c : cclTypes) {
                        double content = contentKg(c, p);
                        if (!i.equals("VUST")) {
                            String w = ou.source;
                            rhs.addTerm(content, x.get(new XKey(w, i, c.type, t)));
                        } else {
                            rhs.addTerm(content, z.get(new ZKey(c.type, t)));
                        }
                    }

                    model.addConstr(
                            I.get(new IKey(i, p, t + 1)),
                            GRB.EQUAL,
                            rhs,
                            "OU_BAL_{-" + i + "_p" + p + "_t" + t + "}");
                }
            }
        }
    }

    private void addServiceLevelConstraints() throws GRBException {
        // PWL mappings
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

            for (int t = startDay; t <= invEndDay; t++) {

                GRBLinExpr rhsFW = new GRBLinExpr();
                rhsFW.addConstant(1.2 * ou.dailyFoodWaterKg);
                rhsFW.addTerm(-0.4 * ou.dailyFoodWaterKg, e);

                model.addConstr(I.get(new IKey(i, "FW", t)), GRB.GREATER_EQUAL, rhsFW,
                        "SERVICE_FW_{i" + i + "_t" + t + "}");

                GRBLinExpr rhsFUEL = new GRBLinExpr();
                rhsFUEL.addTerm(ou.dailyFuelKg, qProduct.get("FUEL"));
                model.addConstr(I.get(new IKey(i, "FUEL", t)), GRB.GREATER_EQUAL, rhsFUEL,
                        "SERVICE_FUEL_{i" + i + "_t" + t + "}");

                GRBLinExpr rhsAMMO = new GRBLinExpr();
                rhsAMMO.addTerm(ou.dailyAmmoKg, qProduct.get("AMMO"));
                model.addConstr(I.get(new IKey(i, "AMMO", t)), GRB.GREATER_EQUAL, rhsAMMO,
                        "SERVICE_AMMO_{" + i + "_t" + t + "}");
            }
        }
    }

    private void addOuCapacityConstraints() throws GRBException {
        for (OperatingUnit ou : ous) {
            String i = ou.operatingUnitName;
            for (int t = startDay; t <= invEndDay; t++) {
                model.addConstr(I.get(new IKey(i, "FW", t)), GRB.LESS_EQUAL, ou.maxFoodWaterKg,
                        "CAP_FW_{i" + i + "_t" + t + "}");
                model.addConstr(I.get(new IKey(i, "FUEL", t)), GRB.LESS_EQUAL, ou.maxFuelKg,
                        "CAP_FUEL_{i" + i + "_t" + t + "}");
                model.addConstr(I.get(new IKey(i, "AMMO", t)), GRB.LESS_EQUAL, ou.maxAmmoKg,
                        "CAP_AMMO_{i" + i + "_t" + t + "}");
            }
        }
    }

    private void addFscCapacityConstraints() throws GRBException {
        for (FSC w : fscs) {
            for (int t = startDay; t <= invEndDay; t++) {
                GRBLinExpr lhs = new GRBLinExpr();
                for (CCLpackage c : cclTypes) {
                    for (String o : ouTypes) {
                        if (o.equals("VUST"))
                            continue;
                        lhs.addTerm(1.0, S.get(new SKey(w.FSCname, c.type, o, t)));
                    }
                }
                model.addConstr(lhs, GRB.LESS_EQUAL, w.maxStorageCapCcls,
                        "FSC_CAP_{w" + w.FSCname + "_t" + t + "}");
            }
        }
    }

    private void addNoOverflowConstraints() throws GRBException {
        // Prevent overflow at arrival time (t+1) under LOW demand.
        // Uses a per-product lower-bound demand multiplier.
        // If you want fully robust against demand being 0, return 0.0 for that product.
        for (int t = startDay; t <= endDay; t++) {

            for (OperatingUnit ou : ous) {
                String i = ou.operatingUnitName;

                for (String p : products) {
                    double cap = capacityOf(ou, p);

                    double dMin = 0.0;

                    GRBLinExpr lhs = new GRBLinExpr();
                    lhs.addTerm(1.0, I.get(new IKey(i, p, t)));
                    lhs.addConstant(-dMin);

                    for (CCLpackage c : cclTypes) {
                        double content = contentKg(c, p);
                        if (!i.equals("VUST")) {
                            lhs.addTerm(content, x.get(new XKey(ou.source, i, c.type, t)));
                        } else {
                            lhs.addTerm(content, z.get(new ZKey(c.type, t)));
                        }
                    }

                    model.addConstr(lhs, GRB.LESS_EQUAL, cap,
                            "NO_OVERFLOW_{i" + i + "_p" + p + "_t" + t + "}");
                }
            }
        }
    }

    private double capacityOf(OperatingUnit ou, String p) {
        return switch (p) {
            case "FW" -> ou.maxFoodWaterKg;
            case "FUEL" -> ou.maxFuelKg;
            case "AMMO" -> ou.maxAmmoKg;
            default -> Double.POSITIVE_INFINITY;
        };
    }

    // PWL functions
    private void precomputePWLFunctions() {
        pwlByProduct.put("FUEL", buildBinomialPWL());
        pwlByProduct.put("AMMO", buildTriangularPWL());
    }

    private PWLData buildTriangularPWL() {
        double a = 0.2;
        double m = 0.8;
        double b = 2.0;

        double[] vBreaks = new double[] {
                0.0, 0.005, 0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.08, 0.1, 0.15, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7,
                0.8, 0.9, 0.95, 0.99, 0.995, 1.0
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

        double prob = Math.pow(1 - p, n);
        double cdf = prob;

        for (int x = 0; x <= n; x++) {
            if (x > 0) {
                prob = prob * (n - x + 1) / x * p / (1 - p);
                cdf += prob;
            }
            double v = 1.0 - cdf;
            vList.add(v);
            double kk = x / 10.0;
            qList.add(kk);
        }

        Collections.reverse(vList);
        Collections.reverse(qList);

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

    // Helpers
    private double meanDemand(OperatingUnit ou, String p) {
        return switch (p) {
            case "FW" -> ou.dailyFoodWaterKg;
            case "FUEL" -> ou.dailyFuelKg;
            case "AMMO" -> ou.dailyAmmoKg;
            default -> 0.0;
        };
    }

    private double contentKg(CCLpackage c, String p) {
        return switch (p) {
            case "FW" -> c.foodWaterKg;
            case "FUEL" -> c.fuelKg;
            case "AMMO" -> c.ammoKg;
            default -> 0.0;
        };
    }
}