package Models;

import com.gurobi.gurobi.*;
import Objects.*;
import DataUtils.sampling;

import java.util.*;

/**
 * MILP for the Capacitated Resupply Problem
 */
public class StochasticMILP {
    private final Instance data;
    private final GRBModel model;

    // Quantiles for the chance constraints
    private final double FWquant;
    private final double FUELquant;
    private final double AMMOquant;


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

    // Number of trucks stationed at MSC
    private GRBVar M;
    // Number of trucks stationed at FSC w
    private final Map<String, GRBVar> K = new HashMap<>();

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

    /**
     * Build the MILP model (derive sets, create variables, add constraints and set objective)
     */
    public StochasticMILP(Instance data, GRBEnv env) throws GRBException {
        this.data = data;
        this.fscs = this.data.FSCs;
        this.ous = this.data.operatingUnits;
        this.products = this.data.products;
        this.cclTypes = this.data.cclTypes;
        this.ouTypes = this.data.ouTypes;

        double epsilon = 0.05; // infeasible if 0.05 / 330??
        sampling quant = new sampling();
        this.FWquant = quant.uniformQuantile(epsilon);
        this.FUELquant = quant.binomialQuantile(epsilon);
        this.AMMOquant = quant.triangularQuantile(epsilon);

        buildArcs();

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
        M = this.model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER, "M");

        // Build K for all FSCs
        for (FSC fsc : this.data.FSCs) {
            K.put(fsc.FSCname, this.model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER, "K_" + fsc.FSCname));
        }

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
    }

    /**
     * Objective : minimize total number of trucks.
     *
     * @throws GRBException if an error occurs
     */
    private void buildObjective() throws GRBException {
        GRBLinExpr obj = new GRBLinExpr();

        obj.addTerm(1.0, this.M);

        for (FSC fsc : this.fscs) {
            obj.addTerm(1.0, K.get(fsc.FSCname));
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

                long dFW = (long) (ou.dailyFoodWaterKg * FWquant);
                long dFuel = (long) (ou.dailyFuelKg * FUELquant);
                long dAmmo = (long) (ou.dailyAmmoKg * AMMOquant);

                for (String p : this.products) {
                    GRBLinExpr rhs = new GRBLinExpr();

                    // Start from current start-of-day inventory
                    rhs.addTerm(1.0, I.get(new IKey(i, p, t)));

                    // Subtract deterministic daily demand
                    double demand = switch (p) {
                        case "FW" -> dFW;
                        case "FUEL" -> dFuel;
                        case "AMMO" -> dAmmo;
                        default ->
                                throw new IllegalStateException("Unexpected value: " + p + ". this.products is likely empty.");
                    };

                    rhs.addConstant(-demand);

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

                    model.addConstr(
                            I.get(new IKey(i, p, t + 1)),
                            GRB.EQUAL,
                            rhs,
                            "OU_BAL_{-" + i + "_p" + p + "_t" + t + "}"
                    );
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
                        I.get(new IKey(i, "FW", t)),
                        GRB.GREATER_EQUAL,
                        ou.dailyFoodWaterKg * FWquant,
                        "NOSTOCK_FW_{i" + i + "_t" + t + "}"
                );

                model.addConstr(
                        I.get(new IKey(i, "FUEL", t)),
                        GRB.GREATER_EQUAL,
                        ou.dailyFuelKg * FUELquant,
                        "NOSTOCK_FUEL_{i" + i + "_t" + t + "}"
                );

                model.addConstr(
                        I.get(new IKey(i, "AMMO", t)),
                        GRB.GREATER_EQUAL,
                        ou.dailyAmmoKg * AMMOquant,
                        "NOSTOCK_AMMO_{" + i + "_t" + t + "}"
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
}

