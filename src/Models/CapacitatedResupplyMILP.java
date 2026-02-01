package Models;
import com.gurobi.gurobi.*;
import Objects.*;
import java.util.*;

public class CapacitatedResupplyMILP {
    private final instance data;
    private final GRBEnv env;
    private final GRBModel model;

    private final int H;

    private static final String MSC = "MSC";
    private static final String FSC1 = "FSC 1";
    private static final String FSC2 = "FSC 2";
    
    private static final String VUST_NAME = "Vust";

    private enum Prod {FW, FUEL, AMMO}

    private final List<OperatingUnit> ous;
    private final List<String> fscs = List.of(FSC1, FSC2);
    private final List<String> cclTypes;

    private final Map<String, OperatingUnit> ouByName = new HashMap<>();
    private final Map<OuType, List<String>> ousByType = new EnumMap<>(OuType.class);

    private final List<ArcF> arcsF = new ArrayList<>();
    private final Map<String, List<String>> ousServedByFsc = new HashMap<>();

    private String vustOuName;

    // FSC capacity in CCLs
    private final Map<String, Integer> fscCapCcls = new HashMap<>();

    private final Map<XKey, GRBVar> x = new HashMap<>();
    private final Map<YKey, GRBVar> y = new HashMap<>();
    private final Map<ZKey, GRBVar> z = new HashMap<>();
    private final Map<IKey, GRBVar> I = new HashMap<>();
    private final Map<SKey, GRBVar> S = new HashMap<>();
    private GRBVar M;
    private final Map<String, GRBVar> K = new HashMap<>();

    private record ArcF(String w, String i) {}
    private record XKey(String w, String i, String c, int t) {}
    private record YKey(String w, String c, OuType o, int t) {}
    private record ZKey(String c, int t) {}
    private record IKey(String i, Prod p, int t) {}
    private record SKey(String w, String c, OuType o, int t) {}

    public CapacitatedResupplyMILP(instance data, GRBEnv env, int horizonDays) throws GRBException {
        this.data = data;
        this.env = env;
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
    
    private void buildVariables() throws GRBException {
        // Trucks
        M = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER, "M");

        K.put(FSC1, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER, "K_FSC1"));
        K.put(FSC2, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER, "K_FSC2"));

        // x variables : FSC -> OU deliveries
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

        // y variables : MSC -> FSC deliveries
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

        // z variables : MSC -> Vust deliveries
        for (String c : cclTypes) {
            for (int t = 1; t <= H; t++) {
                ZKey key = new ZKey(c, t);
                z.put(key, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER, 
                    "z_" + c.replace(" ", "") + "_t" + t
                ));
            }
        }

        // I variables : OU inventory (kg)
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

        // S variables : FSC inventory (#CCL)
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

    private void buildObjective() throws GRBException {
        GRBLinExpr obj = new GRBLinExpr();

        obj.addTerm(1.0, M);
        obj.addTerm(1.0, K.get(FSC1));
        obj.addTerm(1.0, K.get(FSC2));

        model.setObjective(obj, GRB.MINIMIZE);
    }

    private void buildConstraints() throws GRBException {
        addOuInitialInventoryConstraints();
        addFscInitialInventoryConstraints();

        addTruckConstraintsFsc();
        addTruckConstraintsMsc();

        addFscInventoryBalanceConstraints();
        addOuInventoryBalanceConstraints();

        addNoStockOutConstraints();
        addOuCapacityConstraints();
        addFscCapacityConstraints();
    }

    private void addOuInitialInventoryConstraints() throws GRBException {
        for (OperatingUnit ou : ous) {
            String i = ou.operatingUnit;

            model.addConstr(
                I.get(new IKey(i, Prod.FW, 1)),
                GRB.EQUAL,
                ou.maxFoodWaterKg, 
                "OU_INIT_FW_" + i.replace(" ", "")
            );

            model.addConstr(
                I.get(new IKey(i, Prod.FUEL, 1)),
                GRB.EQUAL,
                ou.maxFuelKg,
                "OU_INIT_FUEL_" + i.replace(" ", "")
            );

            model.addConstr(
                I.get(new IKey(i, Prod.AMMO, 1)),
                GRB.EQUAL,
                ou.maxAmmoKg,
                "OU_INIT_AMMO_" + i.replace(" ", "")
            );
        }
    }

    private void addFscInitialInventoryConstraints() throws GRBException {
        Map<String, Integer> cclNameToIndex = buildCclNameToIndexMap();

        for (String w : fscs) {
            Map<String, int[]> initPerOu = data.initialStorageLevels.get(w);

            Map<OuType, long[]> agg = new EnumMap<>(OuType.class);
            for (OuType o : OuType.values()) {
                agg.put(o, new long[]{0L, 0L, 0L});
            }

            for (OperatingUnit ou : ous) {
                String i = ou.operatingUnit;
                int[] arr = initPerOu.get(i);
                if (arr == null) continue;

                OuType o = ou.ouType;
                long[] bucket = agg.get(o);

                bucket[0] += arr[0];
                bucket[1] += arr[1];
                bucket[2] += arr[2];
            }

            for (OuType o : OuType.values()) {
                long[] bucket = agg.get(o);

                for (String cName : cclTypes) {
                    Integer idx = cclNameToIndex.get(cName);
                    
                    model.addConstr(
                        S.get(new SKey(w, cName, o, 1)),
                        GRB.EQUAL,
                        bucket[idx],
                        "FSC_INIT_" + w.replace(" ", "") + "_" + cName.replace(" ", "") + "_" + o
                    );
                }
            }
        }
    }

    private Map<String, Integer> buildCclNameToIndexMap() {
        Map<String, Integer> map = new HashMap<>();

        map.put("1", 0);
        map.put("2", 1);
        map.put("3", 2);

        return map;
    }

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

    private void addOuInventoryBalanceConstraints() throws GRBException {
        for (int t = 1; t <= H - 1; t++) {
            for (OperatingUnit ou : ous) {
                String i = ou.operatingUnit;

                long dFW = ou.dailyFoodWaterKg;
                long dFuel = ou.dailyFuelKg;
                long dAmmo =ou.dailyAmmoKg;

                for (Prod p : Prod.values()) {
                    GRBLinExpr rhs = new GRBLinExpr();

                    rhs.addTerm(1.0, I.get(new IKey(i, p, t)));

                    double demand = switch (p) {
                        case FW -> dFW;
                        case FUEL -> dFuel;
                        case AMMO -> dAmmo;
                    };
                    rhs.addConstant(-demand);

                    for (String c : cclTypes) {
                        CCLpackage pack = data.cclContents.get(c);

                        double content = switch (p) {
                            case FW -> pack.foodWaterKg;
                            case FUEL -> pack.fuelKg;
                            case AMMO -> pack.ammoKg;
                        };

                        for (String w : fscs) {
                            GRBVar xv = x.get(new XKey(w, i, c, t));
                            if (xv != null) {
                                rhs.addTerm(content, xv);
                            }
                        }

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

    public void solve() throws GRBException {
        model.optimize();
    }

    public void dispose() {
        model.dispose();
    }

    public GRBModel getModel() {
        return model;
    }
}
