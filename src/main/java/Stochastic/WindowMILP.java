package Stochastic;

import com.gurobi.gurobi.*;
import Objects.*;

import java.util.*;

/**
 * WindowMILP: solves a MILP over a rolling horizon window [startDay .. endDay]
 * using given starting inventories, and a per-product forecast multiplier.
 *
 * Key design goals:
 *  - Flexible window length H (2,3,4,5,...)
 *  - Per-product forecast multiplier (e.g., FW=1.0, FUEL=1.3, AMMO=1.1)
 *  - Returns ONLY the first-day shipment decisions (x,y,z) to apply in simulation.
 */
public class WindowMILP {
    // -----------------------------
    // Input data / settings
    // -----------------------------
    private final Instance data;
    private final GRBModel model;

    private final List<FSC> fscs;
    private final List<OperatingUnit> ous;
    private final List<String> products;
    private final List<CCLpackage> cclTypes;
    private final List<String> ouTypes;

    private final double M;
    private final Map<String, Double> K;

    private final int startDay;          // global day index (your model uses 1..timeHorizon)
    private final int windowLength;      // requested horizon length H
    private final int endDay;            // inclusive
    private final int lastInvDay;        // endDay+1

    // Forecast multiplier per product, e.g. {FW:1.0, FUEL:1.3, AMMO:1.0}
    private final Map<String, Double> forecastMultiplier;

    // If true: includes your current service-level constraints (FW/FUEL/AMMO min-inventory)
    private final boolean useServiceConstraints;

    // -----------------------------
    // Arcs (FSC -> OU) and Keys
    // -----------------------------
    private final List<Arc> arcs = new ArrayList<>();

    private record Arc(String w, String i) {}
    private record XKey(String w, String i, int c, int t) {}
    private record YKey(String w, int c, String o, int t) {}
    private record ZKey(int c, int t) {}
    private record IKey(String i, String p, int t) {}
    private record SKey(String w, int c, String o, int t) {}

    // -----------------------------
    // Decision variables
    // -----------------------------
    private final Map<XKey, GRBVar> x = new HashMap<>();
    private final Map<YKey, GRBVar> y = new HashMap<>();
    private final Map<ZKey, GRBVar> z = new HashMap<>();
    private final Map<IKey, GRBVar> I = new HashMap<>();
    private final Map<SKey, GRBVar> S = new HashMap<>();

    // Service-level/PWL pieces from your original code
    private GRBVar e;
    private final Map<String, GRBVar> qProduct = new HashMap<>();
    private final Map<String, PWLData> pwlByProduct = new HashMap<>();
    private record PWLData(double[] vBreaks, double[] qBreaks) {}

    // -----------------------------
    // Starting inventories
    // -----------------------------
    // Start-of-day inventories for startDay
    // OU: I0[i][p] in kg
    private final Map<String, Map<String, Double>> I0;

    // FSC: S0[w][o][cType] in #CCL
    private final Map<String, Map<String, Map<Integer, Double>>> S0;

    // -----------------------------
    // Constructor
    // -----------------------------
    public WindowMILP(
            Instance data,
            GRBEnv env,
            double M,
            Map<String, Double> K,
            int startDay,
            int windowLength,
            Map<String, Map<String, Double>> I0,
            Map<String, Map<String, Map<Integer, Double>>> S0,
            Map<String, Double> forecastMultiplier,
            boolean useServiceConstraints,
            boolean verbose
    ) throws GRBException {
        this.data = data;
        this.fscs = data.FSCs;
        this.ous = data.operatingUnits;
        this.products = data.products;
        this.cclTypes = data.cclTypes;
        this.ouTypes = data.ouTypes;

        this.M = M;
        this.K = K;

        this.startDay = startDay;
        this.windowLength = windowLength;

        int proposedEnd = startDay + windowLength - 1;
        this.endDay = Math.min(data.timeHorizon, proposedEnd);
        this.lastInvDay = Math.min(data.timeHorizon, endDay) + 1; // allow inventory t+1 for balances
        // if endDay==timeHorizon, lastInvDay = timeHorizon+1; but your model only defines up to timeHorizon.
        // So clamp:
        int maxInv = data.timeHorizon;

        this.I0 = I0;
        this.S0 = S0;

        this.forecastMultiplier = new HashMap<>(forecastMultiplier);
        // TODO: change multipliers
        for (String p : products) {
            if (p .equals("FW")) {
                this.forecastMultiplier.put(p, 1.0);
            }
            if (p .equals("FUEL")) {
                this.forecastMultiplier.put(p, 1.0);
            }
            if (p .equals("AMMO")) {
                this.forecastMultiplier.put(p, 1.0);
            }
        }

        this.useServiceConstraints = useServiceConstraints;

        env.set(GRB.IntParam.OutputFlag, verbose ? 1 : 0);
        this.model = new GRBModel(env);

        buildArcs();

        if (useServiceConstraints) {
            precomputePWLFunctions();
        }

        buildVariables();
        buildConstraints();
        buildObjective();

        model.update();
    }

    public void solve() throws GRBException {
        model.optimize();

        int status = model.get(GRB.IntAttr.Status);
        if (status != GRB.Status.OPTIMAL) {
            throw new GRBException("WindowMILP: status not OPTIMAL, status=" + status);
        }
    }

    // --------------------------------
    // Returns the first-day shipment decisions
    // --------------------------------

    /** Get first-day FSC->OU shipments x[w,i,c,startDay] */
    public Map<String, Integer> getFirstDayXShipments() throws GRBException {
        Map<String, Integer> out = new HashMap<>();
        int t = startDay;
        for (var entry : x.entrySet()) {
            XKey k = entry.getKey();
            if (k.t() != t) continue;
            double val = entry.getValue().get(GRB.DoubleAttr.X);
            out.put("x_" + k.w() + "_" + k.i() + "_c" + k.c(), (int) Math.round(val));
        }
        return out;
    }

    /** Get first-day MSC->FSC shipments y[w,c,o,startDay] */
    public Map<String, Integer> getFirstDayYShipments() throws GRBException {
        Map<String, Integer> out = new HashMap<>();
        int t = startDay;
        for (var entry : y.entrySet()) {
            YKey k = entry.getKey();
            if (k.t() != t) continue;
            double val = entry.getValue().get(GRB.DoubleAttr.X);
            out.put("y_" + k.w() + "_c" + k.c() + "_" + k.o(), (int) Math.round(val));
        }
        return out;
    }

    /** Get first-day MSC->VUST shipments z[c,startDay] */
    public Map<Integer, Integer> getFirstDayZShipments() throws GRBException {
        Map<Integer, Integer> out = new HashMap<>();
        int t = startDay;
        for (var entry : z.entrySet()) {
            ZKey k = entry.getKey();
            if (k.t() != t) continue;
            double val = entry.getValue().get(GRB.DoubleAttr.X);
            out.put(k.c(), (int) Math.round(val));
        }
        return out;
    }

    public void dispose() throws GRBException {
        model.dispose();
    }

    // -----------------------------
    // Build arcs
    // -----------------------------
    private void buildArcs() {
        for (OperatingUnit ou : ous) {
            if (ou.operatingUnitName.equals("VUST")) continue;
            arcs.add(new Arc(ou.source, ou.operatingUnitName));
        }
    }

    // -----------------------------
    // Build variables
    // -----------------------------
    private void buildVariables() throws GRBException {
        // x[w, i, c, t] : FSC -> OU deliveries (#CCL)
        for (Arc a : arcs) {
            String w = a.w();
            String i = a.i();
            for (CCLpackage c : cclTypes) {
                for (int t = startDay; t <= endDay; t++) {
                    XKey key = new XKey(w, i, c.type, t);
                    x.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER,
                            "x_{w" + w + "_i" + i + "_c" + c.type + "_t" + t + "}"));
                }
            }
        }

        // y[w, c, o, t] : MSC -> FSC deliveries (#CCL)
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

        // z[c, t] : MSC -> VUST deliveries (#CCL)
        for (CCLpackage c : cclTypes) {
            for (int t = startDay; t <= endDay; t++) {
                ZKey key = new ZKey(c.type, t);
                z.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER,
                        "z_{c" + c.type + "_t" + t + "}"));
            }
        }

        // I[i, p, t] : OU inventory (kg)
        // Need inventories from startDay up to endDay, and also endDay+1 if endDay < timeHorizon
        int invLast = Math.min(data.timeHorizon, endDay + 1);
        for (OperatingUnit ou : ous) {
            String i = ou.operatingUnitName;
            for (String p : products) {
                for (int t = startDay; t <= invLast; t++) {
                    IKey key = new IKey(i, p, t);
                    I.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS,
                            "I_{i" + i + "_p" + p + "_t" + t + "}"));
                }
            }
        }

        // S[w, c, o, t] : FSC inventory (#CCL)
        for (FSC fsc : fscs) {
            for (CCLpackage c : cclTypes) {
                for (String o : ouTypes) {
                    if (o.equals("VUST")) continue;
                    for (int t = startDay; t <= invLast; t++) {
                        SKey key = new SKey(fsc.FSCname, c.type, o, t);
                        S.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER,
                                "S_{w" + fsc.FSCname + "_c" + c.type + "_o" + o + "_t" + t + "}"));
                    }
                }
            }
        }

        // Optional service variables
        if (useServiceConstraints) {
            e = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "e");
            for (String p : products) {
                qProduct.put(p, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "q_" + p));
            }
        }
    }

    // -----------------------------
    // Constraints
    // -----------------------------
    private void buildConstraints() throws GRBException {
        addTruckConstraintsFsc();
        addTruckConstraintsMsc();

        addInitialInventoryConstraintsFromState();

        addFscInventoryBalanceConstraints();
        addOuInventoryBalanceConstraints();

        addOuCapacityConstraints();
        addFscCapacityConstraints();

        if (useServiceConstraints) {
            addServiceLevelConstraints();
        }
    }

    // Truck capacities per day
    private void addTruckConstraintsFsc() throws GRBException {
        for (FSC w : fscs) {
            for (int t = startDay; t <= endDay; t++) {
                GRBLinExpr lhs = new GRBLinExpr();
                for (Arc a : arcs) {
                    if (!a.w().equals(w.FSCname)) continue;
                    for (CCLpackage c : cclTypes) {
                        lhs.addTerm(1.0, x.get(new XKey(w.FSCname, a.i(), c.type, t)));
                    }
                }
                model.addConstr(lhs, GRB.LESS_EQUAL, K.get(w.FSCname), "TRUCK_FSC_{w" + w.FSCname + "_t" + t + "}");
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

    /**
     * Initial inventory constraints at startDay:
     * I(i,p,startDay) = I0
     * S(w,c,o,startDay) = S0
     */
    private void addInitialInventoryConstraintsFromState() throws GRBException {
        // OU
        for (OperatingUnit ou : ous) {
            String i = ou.operatingUnitName;
            Map<String, Double> inventoryPerProduct = I0.get(i);
            for (String p : products) {
                Double v = inventoryPerProduct.get(p);
                model.addConstr(I.get(new IKey(i, p, startDay)), GRB.EQUAL, v, "INIT_I_{i" + i + "_p" + p + "}");
            }
        }

        // FSC
        for (FSC fsc : fscs) {
            String w = fsc.FSCname;
            Map<String, Map<Integer, Double>> perType = S0.get(w);

            for (String o : ouTypes) {
                if (o.equals("VUST")) continue;
                Map<Integer, Double> perCCL = perType.get(o);

                for (CCLpackage ccl : cclTypes) {
                    Double v = perCCL.get(ccl.type);
                    model.addConstr(S.get(new SKey(w, ccl.type, o, startDay)), GRB.EQUAL, v,
                            "INIT_S_{w" + w + "_o" + o + "_c" + ccl.type + "}");
                }
            }
        }
    }

    // FSC inventory balance for t=startDay..endDay-1
    private void addFscInventoryBalanceConstraints() throws GRBException {
        int invLast = Math.min(data.timeHorizon, endDay + 1);
        for (int t = startDay; t <= endDay; t++) {
            // if t+1 is outside inventory vars, skip
            if (t + 1 > invLast) break;

            for (FSC w : fscs) {
                for (CCLpackage c : cclTypes) {
                    for (String o : ouTypes) {
                        if (o.equals("VUST")) continue;

                        GRBLinExpr rhs = new GRBLinExpr();
                        rhs.addTerm(1.0, S.get(new SKey(w.FSCname, c.type, o, t)));

                        // subtract outbound x to OUs of type o sourced by w
                        List<OperatingUnit> ousOfType = new ArrayList<>();
                        for (OperatingUnit ou : ous) {
                            if (ou.ouType.equals(o)) ousOfType.add(ou);
                        }
                        for (OperatingUnit ou : ousOfType) {
                            if (ou.source.equals(w.FSCname) && !ou.operatingUnitName.equals("VUST")) {
                                rhs.addTerm(-1.0, x.get(new XKey(w.FSCname, ou.operatingUnitName, c.type, t)));
                            }
                        }

                        // add inbound y from MSC
                        rhs.addTerm(1.0, y.get(new YKey(w.FSCname, c.type, o, t)));

                        model.addConstr(
                                S.get(new SKey(w.FSCname, c.type, o, t + 1)),
                                GRB.EQUAL,
                                rhs,
                                "FSC_BAL_{w" + w.FSCname + "_c" + c.type + "_o" + o + "_t" + t + "}"
                        );
                    }
                }
            }
        }
    }

    // OU inventory balance: I(i,p,t+1) = I(i,p,t) - forecastDemand(i,p,t) + deliveries(t)
    private void addOuInventoryBalanceConstraints() throws GRBException {
        int invLast = Math.min(data.timeHorizon, endDay + 1);

        for (int t = startDay; t <= endDay; t++) {
            if (t + 1 > invLast) break;

            for (OperatingUnit ou : ous) {
                String i = ou.operatingUnitName;

                for (String p : products) {
                    GRBLinExpr rhs = new GRBLinExpr();
                    rhs.addTerm(1.0, I.get(new IKey(i, p, t)));

                    // forecast demand (mean * multiplier per product)
                    double mu = meanDemand(ou, p);
                    double mult = this.forecastMultiplier.get(p);
                    double dem = mult * mu;
                    rhs.addConstant(-dem);

                    // deliveries shipped on day t
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
                            "OU_BAL_{i" + i + "_p" + p + "_t" + t + "}"
                    );
                }
            }
        }
    }

    private void addOuCapacityConstraints() throws GRBException {
        int invLast = Math.min(data.timeHorizon, endDay + 1);
        for (OperatingUnit ou : ous) {
            String i = ou.operatingUnitName;
            for (int t = startDay; t <= invLast; t++) {
                model.addConstr(I.get(new IKey(i, "FW", t)),   GRB.LESS_EQUAL, ou.maxFoodWaterKg, "CAP_FW_{i" + i + "_t" + t + "}");
                model.addConstr(I.get(new IKey(i, "FUEL", t)), GRB.LESS_EQUAL, ou.maxFuelKg,      "CAP_FUEL_{i" + i + "_t" + t + "}");
                model.addConstr(I.get(new IKey(i, "AMMO", t)), GRB.LESS_EQUAL, ou.maxAmmoKg,      "CAP_AMMO_{i" + i + "_t" + t + "}");
            }
        }
    }

    private void addFscCapacityConstraints() throws GRBException {
        int invLast = Math.min(data.timeHorizon, endDay + 1);
        for (FSC w : fscs) {
            for (int t = startDay; t <= invLast; t++) {
                GRBLinExpr lhs = new GRBLinExpr();
                for (CCLpackage c : cclTypes) {
                    for (String o : ouTypes) {
                        if (o.equals("VUST")) continue;
                        lhs.addTerm(1.0, S.get(new SKey(w.FSCname, c.type, o, t)));
                    }
                }
                model.addConstr(lhs, GRB.LESS_EQUAL, w.maxStorageCapCcls, "FSC_CAP_{w" + w.FSCname + "_t" + t + "}");
            }
        }
    }

    // Optional: service-level constraints (ported from your original)
    // NOTE: Your original objective/minimization of e can make the model overly conservative.
    // In a rolling horizon, I recommend either:
    //  - FIX e to a chosen value, OR
    //  - remove these constraints and instead tune forecastMultiplier, OR
    //  - add explicit stockout slack variables and penalize them.
    private void addServiceLevelConstraints() throws GRBException {
        // Precomputed
        for (String p : List.of("FUEL", "AMMO")) {
            model.addGenConstrPWL(
                    e,
                    qProduct.get(p),
                    pwlByProduct.get(p).vBreaks(),
                    pwlByProduct.get(p).qBreaks(),
                    "PWL_" + p
            );
        }

        // IMPORTANT: consider fixing e to avoid e->0 worst-case
        // model.addConstr(e, GRB.EQUAL, 0.10, "FIX_E");

        int invLast = Math.min(data.timeHorizon, endDay + 1);
        for (OperatingUnit ou : ous) {
            String i = ou.operatingUnitName;
            for (int t = startDay; t <= invLast; t++) {
                // FW: same as your original
                GRBLinExpr rhsFW = new GRBLinExpr();
                rhsFW.addConstant(1.2 * ou.dailyFoodWaterKg);
                rhsFW.addTerm(-0.4 * ou.dailyFoodWaterKg, e);
                model.addConstr(I.get(new IKey(i, "FW", t)), GRB.GREATER_EQUAL, rhsFW, "SERVICE_FW_{i" + i + "_t" + t + "}");

                // FUEL
                GRBLinExpr rhsFUEL = new GRBLinExpr();
                rhsFUEL.addTerm(ou.dailyFuelKg, qProduct.get("FUEL"));
                model.addConstr(I.get(new IKey(i, "FUEL", t)), GRB.GREATER_EQUAL, rhsFUEL, "SERVICE_FUEL_{i" + i + "_t" + t + "}");

                // AMMO
                GRBLinExpr rhsAMMO = new GRBLinExpr();
                rhsAMMO.addTerm(ou.dailyAmmoKg, qProduct.get("AMMO"));
                model.addConstr(I.get(new IKey(i, "AMMO", t)), GRB.GREATER_EQUAL, rhsAMMO, "SERVICE_AMMO_{i" + i + "_t" + t + "}");
            }
        }
    }

    // -----------------------------
    // Objective
    // -----------------------------
    private void buildObjective() throws GRBException {
        GRBLinExpr obj = new GRBLinExpr();

        // Minimal objective: minimize shipments (to avoid flooding inventory).
        // You can reweight these later.
        for (GRBVar v : x.values()) obj.addTerm(1.0, v);
        for (GRBVar v : y.values()) obj.addTerm(1.0, v);
        for (GRBVar v : z.values()) obj.addTerm(1.0, v);

        // If you enable service constraints and want to trade off risk, you can add e with a weight:
        // if (useServiceConstraints) obj.addTerm(1000.0, e);

        model.setObjective(obj, GRB.MINIMIZE);
    }

    // -----------------------------
    // Helpers
    // -----------------------------
    private double meanDemand(OperatingUnit ou, String p) {
        return switch (p) {
            case "FW" -> ou.dailyFoodWaterKg;
            case "FUEL" -> ou.dailyFuelKg;
            case "AMMO" -> ou.dailyAmmoKg;
            default -> throw new IllegalArgumentException("Unknown product: " + p);
        };
    }

    private double contentKg(CCLpackage c, String p) {
        return switch (p) {
            case "FW" -> c.foodWaterKg;
            case "FUEL" -> c.fuelKg;
            case "AMMO" -> c.ammoKg;
            default -> throw new IllegalArgumentException("Unknown product: " + p);
        };
    }

    // Your PWL precompute (copied style from your code)
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
            double k = x / 10.0;
            qList.add(k);
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
}