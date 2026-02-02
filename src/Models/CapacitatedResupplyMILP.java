package Models;
import com.gurobi.gurobi.*;
import Objects.*;
import java.util.*;

/**
 * MILP for the Capacitated Resupply Problem
 */
public class CapacitatedResupplyMILP {
    private final instance data;
    private final GRBModel model;

    // Planning horizon length (in days)
    private final int H;

    // Forward Supply Centres
    private static final String FSC1 = "FSC 1";
    private static final String FSC2 = "FSC 2";
    
    // The vust operating unit (is supplied directly from the Main Supply Centre)
    private static final String VUST_NAME = "Vust";

    // Product categories in CCLs 
    private enum Prod {FW, FUEL, AMMO}

    // Lists of all OUs, FSCs and CCL types
    private final List<OperatingUnit> ous;
    private final List<String> fscs = List.of(FSC1, FSC2);
    private final List<String> cclTypes;

    private final Map<String, OperatingUnit> ouByName = new HashMap<>();
    // Used to enforce that FSC inventory for OU type o can only be delivered to OUs of that type
    private final Map<OuType, List<String>> ousByType = new EnumMap<>(OuType.class);

    // Feasible FSC -> OU arcs (w, i)
    private final List<ArcF> arcsF = new ArrayList<>();
    // Maps FSC -> OUs served by that FSC
    private final Map<String, List<String>> ousServedByFsc = new HashMap<>();

    private String vustOuName;

    // FSC capacity in CCLs
    private final Map<String, Integer> fscCapCcls = new HashMap<>();

    // x[w, i, c, t] = number of CCLs shipped from FSC w to OU i of CCL-type c on day t
    private final Map<XKey, GRBVar> x = new HashMap<>();
    // y[w, c, o, t] = number of CCLs shipped from MSC to FSC w of CCL-type c intended for OU-tyupe o on day t
    private final Map<YKey, GRBVar> y = new HashMap<>();
    // z[c, t] = number of CCLs shipped from MSC directly to Vust of CCL-type c on day t
    private final Map<ZKey, GRBVar> z = new HashMap<>();
    // T[i, p, t] = inventory in kg at start of day t at OU i for product p
    private final Map<IKey, GRBVar> I = new HashMap<>();
    // S[w, c, o, t] = inventory in number of CCLs at start of day t at FSC w for CCL-type c and OU-type o
    private final Map<SKey, GRBVar> S = new HashMap<>();
    // Number of trucks stationed at MSC
    private GRBVar M;
    // Number of trucks stationed at FSC w
    private final Map<String, GRBVar> K = new HashMap<>();

    // Record keys for map indexing
    private record ArcF(String w, String i) {}
    private record XKey(String w, String i, String c, int t) {}
    private record YKey(String w, String c, OuType o, int t) {}
    private record ZKey(String c, int t) {}
    private record IKey(String i, Prod p, int t) {}
    private record SKey(String w, String c, OuType o, int t) {}

    /**
     * Build the MILP model (derive sets, create variables, add constraints and set objective)
     */
    public CapacitatedResupplyMILP(instance data, GRBEnv env, int horizonDays) throws GRBException {
        this.data = data;
        this.H = horizonDays;

        this.ous = data.operatingUnits;

        this.cclTypes = new ArrayList<>(data.cclContents.keySet());
        this.cclTypes.sort(Comparator.naturalOrder());

        buildSets();

        this.model = new GRBModel(env);

        buildVariables();
        buildConstraints();
        buildObjective();

        model.update();
    }

    /**
     * Build the needed sets
     */
    private void buildSets() {
        for (OperatingUnit ou : ous) {
            ouByName.put(ou.operatingUnit, ou);

            ousByType.computeIfAbsent(ou.ouType, k -> new ArrayList<>()).add(ou.operatingUnit);

            if (ou.operatingUnit.equals(VUST_NAME)) {
                vustOuName = ou.operatingUnit;
            }
        }

        ousServedByFsc.put(FSC1, new ArrayList<>());
        ousServedByFsc.put(FSC2, new ArrayList<>());

        for (OperatingUnit ou : ous) {
            String src = ou.source;
            if (src == null) continue;

            if (src.equals(FSC1) || src.equals(FSC2)) {
                arcsF.add(new ArcF(src, ou.operatingUnit));
                ousServedByFsc.get(src).add(ou.operatingUnit);
            }
        }

        Centre c1 = data.sourceCapacities.get(FSC1);
        Centre c2 = data.sourceCapacities.get(FSC2);

        fscCapCcls.put(FSC1, c1.maxStorageCapCcls);
        fscCapCcls.put(FSC2, c2.maxStorageCapCcls);
    }
    
    /**
     * Create all decision variables.
     * @throws GRBException if an error occurs
     */
    private void buildVariables() throws GRBException {
        // Trucks (stationed at MSC, FSC1 or FSC2)
        M = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER, "M");
        K.put(FSC1, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER, "K_FSC1"));
        K.put(FSC2, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER, "K_FSC2"));

        // x[w, i, c, t] : FSC -> OU deliveries
        for (ArcF a : arcsF) {
            String w = a.w();
            String i = a.i();
            for (String c : cclTypes) {
                for (int t = 1; t <= H; t++) {
                    XKey key = new XKey(w, i, c, t);
                    x.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER, 
                        "x_" + w.replace(" ", "") + "_" +
                        i.replace(" ", "") + "_" +
                        c.replace(" ", "") + "_t" + t
                    ));
                }
            }
        }

        // y[w, c, o, t] : MSC -> FSC deliveries
        for (String w : fscs) {
            for (String c : cclTypes) {
                for (OuType o : OuType.values()) {
                    for (int t = 1; t <= H; t++) {
                        YKey key = new YKey(w, c, o, t);
                        y.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER,
                            "y_" + w.replace(" ", "") + "_" +
                            c.replace(" ", "") + "_" +
                            o + "_t" + t
                        ));
                    }
                }
            }
        }

        // z[c, t] : MSC -> Vust deliveries
        for (String c : cclTypes) {
            for (int t = 1; t <= H; t++) {
                ZKey key = new ZKey(c, t);
                z.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER, 
                    "z_" + c.replace(" ", "") + "_t" + t
                ));
            }
        }

        // I[i, p, t]: OU inventory (kg)
        for (OperatingUnit ou : ous) {
            String i = ou.operatingUnit;
            for (Prod p : Prod.values()) {
                for (int t = 1; t <= H; t++) {
                    IKey key = new IKey(i, p, t);
                    I.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, 
                        "I_" + i.replace(" ", "") + "_" + p + "_t" + t
                    ));
                }
            }
        }

        // S[w, c, o, t] : FSC inventory (#CCL)
        for (String w : fscs) {
            for (String c : cclTypes) {
                for (OuType o : OuType.values()) {
                    for (int t = 1; t <= H; t++) {
                        SKey key = new SKey(w, c, o, t);
                        S.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER, 
                            "S_" + w.replace(" ", "") + "_" +
                            c.replace(" ", "") + "_" + 
                            o + "_t" + t
                        ));
                    }
                }
            }
        }
    }

    /**
     * Objective : minimize total number of trucks. 
     * @throws GRBException if an error occurs
     */
    private void buildObjective() throws GRBException {
        GRBLinExpr obj = new GRBLinExpr();

        obj.addTerm(1.0, M);
        obj.addTerm(1.0, K.get(FSC1));
        obj.addTerm(1.0, K.get(FSC2));

        model.setObjective(obj, GRB.MINIMIZE);
    }

    /**
     * Add all constraints to the model.
     * @throws GRBException if an error occurs
     */
    private void buildConstraints() throws GRBException {
        // Initial inventories
        addOuInitialInventoryConstraints();
        addFscInitialInventoryConstraints();

        // Truck departure capacities per day
        addTruckConstraintsFsc();
        addTruckConstraintsMsc();

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
     * @throws GRBException if an error occurs
     */
    private void addOuInitialInventoryConstraints() throws GRBException {
        for (OperatingUnit ou : ous) {
            String i = ou.operatingUnit;

            // Food & Water initial inventory
            model.addConstr(
                I.get(new IKey(i, Prod.FW, 1)),
                GRB.EQUAL,
                ou.maxFoodWaterKg, 
                "OU_INIT_FW_" + i.replace(" ", "")
            );

            // Fuel initial inventory
            model.addConstr(
                I.get(new IKey(i, Prod.FUEL, 1)),
                GRB.EQUAL,
                ou.maxFuelKg,
                "OU_INIT_FUEL_" + i.replace(" ", "")
            );

            // Ammunition initial inventory
            model.addConstr(
                I.get(new IKey(i, Prod.AMMO, 1)),
                GRB.EQUAL,
                ou.maxAmmoKg,
                "OU_INIT_AMMO_" + i.replace(" ", "")
            );
        }
    }

    /**
     * Set FSC starting inventory based on initial storage in the data. 
     * @throws GRBException if an error occurs
     */
    private void addFscInitialInventoryConstraints() throws GRBException {
        Map<String, Integer> cclNameToIndex = buildCclNameToIndexMap();

        for (String w : fscs) {
            Map<String, int[]> initByType = data.initialStorageLevels.get(w);
            if (initByType == null) {
                throw new IllegalStateException("Missing initial storage table for " + w);
            }

            for (OuType o : OuType.values()) {
                int[] arr = initByType.get(o.name());
                if (arr == null) {
                    arr = new int[]{0, 0, 0};
                }

                for (String cName : cclTypes) {
                    Integer idx = cclNameToIndex.get(cName);
                    if (idx == null) {
                        throw new IllegalStateException("Unknown CCL type: " + cName);
                    }

                    model.addConstr(
                            S.get(new SKey(w, cName, o, 1)),
                            GRB.EQUAL,
                            arr[idx],
                            "FSC_INIT_" + w.replace(" ", "") + "_" + cName.replace(" ", "") + "_" + o
                    );
                }
            }
        }
    }


    /**
     * Map CCL type names to indices of the int[3] initial storage arrays.
     * @return the map
     */
    private Map<String, Integer> buildCclNameToIndexMap() {
        Map<String, Integer> map = new HashMap<>();

        map.put("1", 0);
        map.put("2", 1);
        map.put("3", 2);

        return map;
    }

    /**
     * FSC truck constraint (one CCL per truck per day)
     * @throws GRBException if an error occurs
     */
    private void addTruckConstraintsFsc() throws GRBException {
        for (String w : fscs) {
            for (int t = 1; t <= H; t++) {
                GRBLinExpr lhs = new GRBLinExpr();

                for (ArcF a : arcsF) {
                    if (!a.w().equals(w)) continue;

                    for (String c : cclTypes) {
                        GRBVar var = x.get(new XKey(w, a.i(), c, t));
                        lhs.addTerm(1.0, var);
                    }
                }

                model.addConstr(
                    lhs,
                    GRB.LESS_EQUAL,
                    K.get(w),
                    "TRUCK_FSC_" + w.replace(" ", "") + "_t" + t
                );
            }
        }
    }

    /**
     * MSC truck constraint (one CCL per truck per day)
     * @throws GRBException if an error occurs
     */
    private void addTruckConstraintsMsc() throws GRBException {
        for (int t = 1; t <= H; t++) {
            GRBLinExpr lhs = new GRBLinExpr();

            for (String w : fscs) {
                for (String c : cclTypes) {
                    for (OuType o : OuType.values()) {
                        GRBVar var = y.get(new YKey(w, c, o, t));
                        lhs.addTerm(1.0, var);
                    }
                }
            }

            for (String c : cclTypes) {
                lhs.addTerm(1.0, z.get(new ZKey(c, t)));
            }

            model.addConstr(lhs, GRB.LESS_EQUAL, M, "TRUCK_MSC_t" + t);
        }
    }

    /**
     * FSC inventory balance constraints.
     * @throws GRBException if an error occurs
     */
    private void addFscInventoryBalanceConstraints() throws GRBException {
        for (int t = 1; t <= H - 1; t++) {
            for (String w : fscs) {
                for (String c : cclTypes) {
                    for (OuType o : OuType.values()) {
                        GRBLinExpr rhs = new GRBLinExpr();

                        rhs.addTerm(1.0, S.get(new SKey(w, c, o, t)));

                        List<String> ousOfType = ousByType.get(o);
                        for (String i : ousOfType) {
                            XKey xk = new XKey(w, i, c, t);
                            GRBVar xv = x.get(xk);
                            if (xv != null) {
                                rhs.addTerm(-1.0, xv);
                            }
                        }

                        rhs.addTerm(1.0, y.get(new YKey(w, c, o, t)));

                        model.addConstr(
                            S.get(new SKey(w, c, o, t + 1)),
                            GRB.EQUAL,
                            rhs,
                            "FSC_BAL_" + w.replace(" ", "") + "_c" + c + " " + o + "_t" + t
                        );
                    }
                }
            }
        }
    }

    /**
     * OU inventory balance constraints.
     * @throws GRBException if an error occurs
     */
    private void addOuInventoryBalanceConstraints() throws GRBException {
        for (int t = 1; t <= H - 1; t++) {
            for (OperatingUnit ou : ous) {
                String i = ou.operatingUnit;

                long dFW = ou.dailyFoodWaterKg;
                long dFuel = ou.dailyFuelKg;
                long dAmmo =ou.dailyAmmoKg;

                for (Prod p : Prod.values()) {
                    GRBLinExpr rhs = new GRBLinExpr();

                    // Start from current start-of-day inventory
                    rhs.addTerm(1.0, I.get(new IKey(i, p, t)));

                    // Subtract deterministic daily demand
                    double demand = switch (p) {
                        case FW -> dFW;
                        case FUEL -> dFuel;
                        case AMMO -> dAmmo;
                    };
                    rhs.addConstant(-demand);

                    // Add deliveries shipped on day t (available at t + 1)
                    for (String c : cclTypes) {
                        CCLpackage pack = data.cclContents.get(c);

                        double content = switch (p) {
                            case FW -> pack.foodWaterKg;
                            case FUEL -> pack.fuelKg;
                            case AMMO -> pack.ammoKg;
                        };

                        // Contributions from FSC shipments x
                        for (String w : fscs) {
                            GRBVar xv = x.get(new XKey(w, i, c, t));
                            if (xv != null) {
                                rhs.addTerm(content, xv);
                            }
                        }

                        // Deliveries from MSC -> Vust
                        if (i.equals(vustOuName)) {
                            rhs.addTerm(content, z.get(new ZKey(c, t)));
                        }
                    }

                    model.addConstr(
                        I.get(new IKey(i, p, t + 1)),
                        GRB.EQUAL,
                        rhs,
                        "OU_BAL_" + i.replace(" ", "") + "_" + p + "_t" + t
                    );
                }
            }
        }
    }

    /**
     * No stock-out constraints. 
     * @throws GRBException if an error occurs
     */
    private void addNoStockOutConstraints() throws GRBException {
        for (OperatingUnit ou : ous) {
            String i = ou.operatingUnit;

            for (int t = 1; t <= H; t++) {
                model.addConstr(
                    I.get(new IKey(i, Prod.FW, t)),
                    GRB.GREATER_EQUAL,
                    ou.dailyFoodWaterKg,
                    "NOSTOCK_FW_" + i.replace(" ", "") + "_t" + t
                );

                model.addConstr(
                    I.get(new IKey(i, Prod.FUEL, t)),
                    GRB.GREATER_EQUAL,
                    ou.dailyFuelKg,
                    "NOSTOCK_FUEL_" + i.replace(" ", "") + "_t" + t
                );

                model.addConstr(
                    I.get(new IKey(i, Prod.AMMO, t)),
                    GRB.GREATER_EQUAL,
                    ou.dailyAmmoKg,
                    "NOSTOCK_AMMO_" + i.replace(" ", "") + "_t" + t
                );
            }
        }
    }

    /**
     * OU storage capacity constraints. 
     * @throws GRBException if an error occurs
     */
    private void addOuCapacityConstraints() throws GRBException {
        for (OperatingUnit ou : ous) {
            String i = ou.operatingUnit;

            for (int t = 1; t <= H; t++) {

                model.addConstr(
                    I.get(new IKey(i, Prod.FW, t)),
                    GRB.LESS_EQUAL,
                    ou.maxFoodWaterKg,
                    "CAP_FW_" + i.replace(" ", "") + "_t" + t
                );

                model.addConstr(
                    I.get(new IKey(i, Prod.FUEL, t)),
                    GRB.LESS_EQUAL,
                    ou.maxFuelKg,
                    "CAP_FUEL_" + i.replace(" ", "") + "_t" + t
                );

                model.addConstr(
                    I.get(new IKey(i, Prod.AMMO, t)),
                    GRB.LESS_EQUAL,
                    ou.maxAmmoKg,
                    "CAP_AMMO_" + i.replace(" ", "") + "_t" + t
                );
            }
        }
    }

    /**
     * FSC capacity constraints (in CCL units).
     * @throws GRBException if an error occurs
     */
    private void addFscCapacityConstraints() throws GRBException {
        for (String w : fscs) {
            Integer cap = fscCapCcls.get(w);
            
            for (int t = 1; t <= H; t++) {
                GRBLinExpr lhs = new GRBLinExpr();

                for (String c : cclTypes) {
                    for (OuType o : OuType.values()) {
                        lhs.addTerm(1.0, S.get(new SKey(w, c, o, t)));
                    }
                }

                model.addConstr(
                    lhs, 
                    GRB.LESS_EQUAL,
                    cap,
                    "FSC_CAP_" + w.replace(" ", "") + "_t" + t
                );
            }
        }
    }

    /**
     * Optimize the model with Gurobi.
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
     * @return the Gurobi model
     */
    public GRBModel getModel() {
        return model;
    }
}
