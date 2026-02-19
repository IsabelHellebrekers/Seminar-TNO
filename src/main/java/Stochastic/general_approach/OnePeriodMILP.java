package Stochastic.general_approach;

import Objects.CCLpackage;
import Objects.FSC;
import Objects.Instance;
import Objects.OperatingUnit;

import com.gurobi.gurobi.*;

import java.util.*;

public class OnePeriodMILP {

    // ---- Keys ----
    private record Arc(String w, String i) {}
    private record XKey(String w, String i, int c, int t) {}
    private record YKey(String w, int c, String o, int t) {}
    private record ZKey(int c, int t) {}
    private record IKey(String i, String p, int t) {}
    private record SKey(String w, int c, String o, int t) {}
    private record PWLData(double[] vBreaks, double[] qBreaks) {}

    /**
     * Solve the stochastic-approx MILP and return a NEW instance with updated inventories.
     */
    public Instance resupply(Instance instance) {
        if (instance.timeHorizon <= 0) return deepCopyInstance(instance);

        GRBEnv env = null;
        GRBModel model = null;

        try {
            int T = instance.timeHorizon;

            List<FSC> fscs = instance.FSCs;
            List<OperatingUnit> ous = instance.operatingUnits;
            List<String> products = instance.products;
            List<CCLpackage> cclTypes = instance.cclTypes;
            List<String> ouTypes = instance.ouTypes;

            // Build FSC->OU arcs
            List<Arc> arcs = new ArrayList<>();
            for (OperatingUnit ou : ous) {
                if ("VUST".equals(ou.operatingUnitName)) continue;
                arcs.add(new Arc(ou.source, ou.operatingUnitName));
            }

            // Fixed truck availability
            int M = 40;

            Map<String, Integer> K = new HashMap<>();
            for (FSC f : fscs) K.put(f.FSCname, 30);

            // PWL quantile approximation data
            Map<String, PWLData> pwlByProduct = new HashMap<>();
            pwlByProduct.put("FUEL", buildBinomialPWL());
            pwlByProduct.put("AMMO", buildTriangularPWL());

            // ---- Gurobi init ----
            env = new GRBEnv(true);
            env.set(GRB.IntParam.OutputFlag, 0);
            env.start();
            model = new GRBModel(env);

            // ---- Variables ----
            Map<XKey, GRBVar> x = new HashMap<>();
            Map<YKey, GRBVar> y = new HashMap<>();
            Map<ZKey, GRBVar> z = new HashMap<>();
            Map<IKey, GRBVar> I = new HashMap<>();
            Map<SKey, GRBVar> S = new HashMap<>();

            // x[w,i,c,t]
            for (Arc a : arcs) {
                for (CCLpackage c : cclTypes) {
                    for (int t = 1; t <= T; t++) {
                        XKey key = new XKey(a.w(), a.i(), c.type, t);
                        x.put(key, model.addVar(0, GRB.INFINITY, 0, GRB.INTEGER,
                                "x_{w" + a.w() + "_i" + a.i() + "_c" + c.type + "_t" + t + "}"));
                    }
                }
            }

            // y[w,c,o,t]
            for (FSC fsc : fscs) {
                for (CCLpackage c : cclTypes) {
                    for (String o : ouTypes) {
                        if ("VUST".equals(o)) continue; // in your deterministic model, FSC storage skips VUST type
                        for (int t = 1; t <= T; t++) {
                            YKey key = new YKey(fsc.FSCname, c.type, o, t);
                            y.put(key, model.addVar(0, GRB.INFINITY, 0, GRB.INTEGER,
                                    "y_{w" + fsc.FSCname + "_c" + c.type + "_o" + o + "_t" + t + "}"));
                        }
                    }
                }
            }

            // z[c,t]
            for (CCLpackage c : cclTypes) {
                for (int t = 1; t <= T; t++) {
                    ZKey key = new ZKey(c.type, t);
                    z.put(key, model.addVar(0, GRB.INFINITY, 0, GRB.INTEGER,
                            "z_{c" + c.type + "_t" + t + "}"));
                }
            }

            // I[i,p,t]
            for (OperatingUnit ou : ous) {
                for (String p : products) {
                    for (int t = 1; t <= T; t++) {
                        IKey key = new IKey(ou.operatingUnitName, p, t);
                        I.put(key, model.addVar(0, GRB.INFINITY, 0, GRB.CONTINUOUS,
                                "I_{i" + ou.operatingUnitName + "_p" + p + "_t" + t + "}"));
                    }
                }
            }

            // S[w,c,o,t]
            for (FSC fsc : fscs) {
                for (CCLpackage c : cclTypes) {
                    for (String o : ouTypes) {
                        if ("VUST".equals(o)) continue;
                        for (int t = 1; t <= T; t++) {
                            SKey key = new SKey(fsc.FSCname, c.type, o, t);
                            S.put(key, model.addVar(0, GRB.INFINITY, 0, GRB.INTEGER,
                                    "S_{w" + fsc.FSCname + "_c" + c.type + "_o" + o + "_t" + t + "}"));
                        }
                    }
                }
            }

            // e: worst (approx) stockout probability
            GRBVar e = model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, "e");

            // quantile multipliers q_p
            Map<String, GRBVar> q = new HashMap<>();
            for (String p : products) {
                q.put(p, model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "q_" + p));
            }

            model.update();

            // ---- Constraints ----

            // (1) FSC truck constraints: sum_{i,c} x[w,i,c,t] <= K[w]
            for (FSC fsc : fscs) {
                String w = fsc.FSCname;
                int cap = K.get(w);

                for (int t = 1; t <= T; t++) {
                    GRBLinExpr lhs = new GRBLinExpr();
                    for (Arc a : arcs) {
                        if (!w.equals(a.w())) continue;
                        for (CCLpackage c : cclTypes) {
                            lhs.addTerm(1.0, x.get(new XKey(w, a.i(), c.type, t)));
                        }
                    }
                    model.addConstr(lhs, GRB.LESS_EQUAL, cap, "TRUCK_FSC_{w" + w + "_t" + t + "}");
                }
            }

            // (2) MSC truck constraints: sum_{w,c,o} y + sum_{c} z <= M
            for (int t = 1; t <= T; t++) {
                GRBLinExpr lhs = new GRBLinExpr();

                for (FSC fsc : fscs) {
                    for (CCLpackage c : cclTypes) {
                        for (String o : ouTypes) {
                            if ("VUST".equals(o)) continue;
                            lhs.addTerm(1.0, y.get(new YKey(fsc.FSCname, c.type, o, t)));
                        }
                    }
                }
                for (CCLpackage c : cclTypes) {
                    lhs.addTerm(1.0, z.get(new ZKey(c.type, t)));
                }

                model.addConstr(lhs, GRB.LESS_EQUAL, M, "TRUCK_MSC_t" + t);
            }

            // (3) Initial OU inventories = current ou.storageLevels
            for (OperatingUnit ou : ous) {
                String i = ou.operatingUnitName;
                model.addConstr(I.get(new IKey(i, "FW", 1)), GRB.EQUAL, ou.storageLevelFWKg, "OU_INIT_FW_" + i);
                model.addConstr(I.get(new IKey(i, "FUEL", 1)), GRB.EQUAL, ou.storageLevelFuelKg, "OU_INIT_FUEL_" + i);
                model.addConstr(I.get(new IKey(i, "AMMO", 1)), GRB.EQUAL, ou.storageLevelAmmoKg, "OU_INIT_AMMO_" + i);
            }

            // (4) Initial FSC inventories = current fsc.storageLevels
            for (FSC fsc : fscs) {
                String w = fsc.FSCname;
                for (String o : ouTypes) {
                    if ("VUST".equals(o)) continue;
                    int[] productLevels = fsc.storageLevels.get(o);

                    for (CCLpackage c : cclTypes) {
                        int idx = c.type - 1;
                        int initial = productLevels[idx];
                        model.addConstr(S.get(new SKey(w, c.type, o, 1)), GRB.EQUAL, initial,
                                "FSC_INIT_{w" + w + "_c" + c.type + "_o" + o + "}");
                    }
                }
            }

            // (5) FSC balance: S[t+1] = S[t] - x + y
            for (int t = 1; t <= T - 1; t++) {
                for (FSC fsc : fscs) {
                    String w = fsc.FSCname;
                    for (String o : ouTypes) {
                        if ("VUST".equals(o)) continue;

                        for (CCLpackage c : cclTypes) {
                            GRBLinExpr rhs = new GRBLinExpr();
                            rhs.addTerm(1.0, S.get(new SKey(w, c.type, o, t)));

                            // subtract x shipped from FSC to OUs of type o supplied by w
                            for (OperatingUnit ou : ous) {
                                if ("VUST".equals(ou.operatingUnitName)) continue;
                                if (!o.equals(ou.ouType)) continue;
                                if (!w.equals(ou.source)) continue;
                                rhs.addTerm(-1.0, x.get(new XKey(w, ou.operatingUnitName, c.type, t)));
                            }

                            // add y received from MSC
                            rhs.addTerm(1.0, y.get(new YKey(w, c.type, o, t)));

                            model.addConstr(S.get(new SKey(w, c.type, o, t + 1)), GRB.EQUAL, rhs,
                                    "FSC_BAL_{w" + w + "_c" + c.type + "_o" + o + "_t" + t + "}");
                        }
                    }
                }
            }

            // (6) OU balance with mean demand:
            // I[t+1] = I[t] - dailyDemand + deliveries (x for non-VUST, z for VUST)
            for (int t = 1; t <= T - 1; t++) {
                for (OperatingUnit ou : ous) {
                    String i = ou.operatingUnitName;

                    double dFW = ou.dailyFoodWaterKg;
                    double dFuel = ou.dailyFuelKg;
                    double dAmmo = ou.dailyAmmoKg;

                    for (String p : products) {
                        GRBLinExpr rhs = new GRBLinExpr();
                        rhs.addTerm(1.0, I.get(new IKey(i, p, t)));

                        double demand = switch (p) {
                            case "FW" -> dFW;
                            case "FUEL" -> dFuel;
                            case "AMMO" -> dAmmo;
                            default -> throw new IllegalStateException("Unknown product " + p);
                        };
                        rhs.addConstant(-demand);

                        for (CCLpackage c : cclTypes) {
                            double content = switch (p) {
                                case "FW" -> c.foodWaterKg;
                                case "FUEL" -> c.fuelKg;
                                case "AMMO" -> c.ammoKg;
                                default -> throw new IllegalStateException("Unknown product " + p);
                            };

                            if (!"VUST".equals(i)) {
                                String w = ou.source;
                                rhs.addTerm(content, x.get(new XKey(w, i, c.type, t)));
                            } else {
                                rhs.addTerm(content, z.get(new ZKey(c.type, t)));
                            }
                        }

                        model.addConstr(I.get(new IKey(i, p, t + 1)), GRB.EQUAL, rhs,
                                "OU_BAL_{i" + i + "_p" + p + "_t" + t + "}");
                    }
                }
            }

            // (7) PWL mappings for FUEL and AMMO: q_p = Q_p(1-e)
            {
                PWLData fuel = pwlByProduct.get("FUEL");
                model.addGenConstrPWL(e, q.get("FUEL"), fuel.vBreaks(), fuel.qBreaks(), "PWL_FUEL");

                PWLData ammo = pwlByProduct.get("AMMO");
                model.addGenConstrPWL(e, q.get("AMMO"), ammo.vBreaks(), ammo.qBreaks(), "PWL_AMMO");
            }

            // (8) Service-level constraints:
            // I[i,p,t] >= dailyDemand(i,p) * quantileMultiplier(1-e)
            for (OperatingUnit ou : ous) {
                String i = ou.operatingUnitName;

                for (int t = 1; t <= T; t++) {
                    // FW uniform [0.8,1.2] => quantile(1-e) = 1.2 - 0.4*e
                    GRBLinExpr rhsFW = new GRBLinExpr();
                    rhsFW.addConstant(1.2 * ou.dailyFoodWaterKg);
                    rhsFW.addTerm(-0.4 * ou.dailyFoodWaterKg, e);
                    model.addConstr(I.get(new IKey(i, "FW", t)), GRB.GREATER_EQUAL, rhsFW,
                            "SERV_FW_{i" + i + "_t" + t + "}");

                    // FUEL
                    GRBLinExpr rhsFUEL = new GRBLinExpr();
                    rhsFUEL.addTerm(ou.dailyFuelKg, q.get("FUEL"));
                    model.addConstr(I.get(new IKey(i, "FUEL", t)), GRB.GREATER_EQUAL, rhsFUEL,
                            "SERV_FUEL_{i" + i + "_t" + t + "}");

                    // AMMO
                    GRBLinExpr rhsAMMO = new GRBLinExpr();
                    rhsAMMO.addTerm(ou.dailyAmmoKg, q.get("AMMO"));
                    model.addConstr(I.get(new IKey(i, "AMMO", t)), GRB.GREATER_EQUAL, rhsAMMO,
                            "SERV_AMMO_{i" + i + "_t" + t + "}");
                }
            }

            // (9) OU capacity constraints
            for (OperatingUnit ou : ous) {
                String i = ou.operatingUnitName;
                for (int t = 1; t <= T; t++) {
                    model.addConstr(I.get(new IKey(i, "FW", t)), GRB.LESS_EQUAL, ou.maxFoodWaterKg, "CAP_FW_" + i + "_t" + t);
                    model.addConstr(I.get(new IKey(i, "FUEL", t)), GRB.LESS_EQUAL, ou.maxFuelKg, "CAP_FUEL_" + i + "_t" + t);
                    model.addConstr(I.get(new IKey(i, "AMMO", t)), GRB.LESS_EQUAL, ou.maxAmmoKg, "CAP_AMMO_" + i + "_t" + t);
                }
            }

            // (10) FSC capacity constraints in CCL units: sum S <= maxStorageCapCcls
            for (FSC fsc : fscs) {
                String w = fsc.FSCname;
                for (int t = 1; t <= T; t++) {
                    GRBLinExpr lhs = new GRBLinExpr();
                    for (CCLpackage c : cclTypes) {
                        for (String o : ouTypes) {
                            if ("VUST".equals(o)) continue;
                            lhs.addTerm(1.0, S.get(new SKey(w, c.type, o, t)));
                        }
                    }
                    model.addConstr(lhs, GRB.LESS_EQUAL, fsc.maxStorageCapCcls, "FSC_CAP_{w" + w + "_t" + t + "}");
                }
            }

            // ---- Objective: minimize worst stockout probability proxy e ----
            GRBLinExpr obj = new GRBLinExpr();
            obj.addTerm(1.0, e);
            model.setObjective(obj, GRB.MINIMIZE);

            // ---- Solve ----
            model.optimize();
            int status = model.get(GRB.IntAttr.Status);
            System.out.println(status);

            // ---- Apply only first-day shipments (t=1) to return next-state instance ----
            Instance updated = deepCopyInstance(instance);
            applyFirstDay(updated, ous, fscs, cclTypes, ouTypes, arcs, x, y, z);

            return updated;

        } catch (Exception ex) {
            return deepCopyInstance(instance);
        } finally {
            try { if (model != null) model.dispose(); } catch (Exception ignored) {}
            try { if (env != null) env.dispose(); } catch (Exception ignored) {}
        }
    }

    // ---------------- inventory updates (execute t=1) ----------------

    private static void applyFirstDay(Instance updated,
                                      List<OperatingUnit> ous,
                                      List<FSC> fscs,
                                      List<CCLpackage> cclTypes,
                                      List<String> ouTypes,
                                      List<Arc> arcs,
                                      Map<XKey, GRBVar> x,
                                      Map<YKey, GRBVar> y,
                                      Map<ZKey, GRBVar> z) throws GRBException {

        Map<String, OperatingUnit> ouByName = new HashMap<>();
        for (OperatingUnit ou : updated.operatingUnits) ouByName.put(ou.operatingUnitName, ou);

        Map<String, FSC> fscByName = new HashMap<>();
        for (FSC f : updated.FSCs) fscByName.put(f.FSCname, f);

        // 1) Apply OU inbound (x for non-VUST, z for VUST)
        for (OperatingUnit ou : updated.operatingUnits) {
            String i = ou.operatingUnitName;

            double addFW = 0, addFuel = 0, addAmmo = 0;

            if (!"VUST".equals(i)) {
                String w = ou.source;
                for (CCLpackage c : cclTypes) {
                    GRBVar var = x.get(new XKey(w, i, c.type, 1));
                    if (var == null) continue;
                    int qty = (int) Math.round(var.get(GRB.DoubleAttr.X));
                    if (qty <= 0) continue;

                    addFW += qty * c.foodWaterKg;
                    addFuel += qty * c.fuelKg;
                    addAmmo += qty * c.ammoKg;
                }
            } else {
                for (CCLpackage c : cclTypes) {
                    GRBVar var = z.get(new ZKey(c.type, 1));
                    int qty = (int) Math.round(var.get(GRB.DoubleAttr.X));
                    if (qty <= 0) continue;

                    addFW += qty * c.foodWaterKg;
                    addFuel += qty * c.fuelKg;
                    addAmmo += qty * c.ammoKg;
                }
            }

            ou.storageLevelFWKg = Math.min(ou.maxFoodWaterKg, ou.storageLevelFWKg + addFW);
            ou.storageLevelFuelKg = Math.min(ou.maxFuelKg, ou.storageLevelFuelKg + addFuel);
            ou.storageLevelAmmoKg = Math.min(ou.maxAmmoKg, ou.storageLevelAmmoKg + addAmmo);
        }

        // 2) Apply FSC inventory changes for t=1: S := S - x + y
        // Update the *stored* fsc.storageLevels (int arrays)
        for (FSC fsc : updated.FSCs) {
            String w = fsc.FSCname;
            if (fsc.storageLevels == null) continue;

            for (String o : ouTypes) {
                if ("VUST".equals(o)) continue;
                int[] arr = fsc.storageLevels.get(o);
                if (arr == null) continue;

                for (CCLpackage c : cclTypes) {
                    int idx = c.type - 1;
                    if (idx < 0 || idx >= arr.length) continue;

                    int outX = 0;
                    // sum x to OUs of type o from w (t=1)
                    for (OperatingUnit ou : updated.operatingUnits) {
                        if ("VUST".equals(ou.operatingUnitName)) continue;
                        if (!o.equals(ou.ouType)) continue;
                        if (!w.equals(ou.source)) continue;

                        GRBVar xv = x.get(new XKey(w, ou.operatingUnitName, c.type, 1));
                        if (xv != null) outX += (int) Math.round(xv.get(GRB.DoubleAttr.X));
                    }

                    int inY = 0;
                    GRBVar yv = y.get(new YKey(w, c.type, o, 1));
                    if (yv != null) inY = (int) Math.round(yv.get(GRB.DoubleAttr.X));

                    arr[idx] = Math.max(0, arr[idx] - outX + inY);
                }
            }
        }
    }

    // ---------------- PWL builders ----------------

    private static PWLData buildTriangularPWL() {
        double a = 0.2, m = 0.8, b = 2.0;

        double[] vBreaks = new double[]{0.0, 0.005, 0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.08, 0.1, 0.15, 0.2};
        double[] qValues = new double[vBreaks.length];

        for (int k = 0; k < vBreaks.length; k++) {
            double v = vBreaks[k];
            double u = 1.0 - v;

            double Fm = (m - a) / (b - a);
            if (u <= Fm) {
                qValues[k] = a + Math.sqrt(u * (b - a) * (m - a));
            } else {
                qValues[k] = b - Math.sqrt((1.0 - u) * (b - a) * (b - m));
            }
        }
        return new PWLData(vBreaks, qValues);
    }

    private static PWLData buildBinomialPWL() {
        int n = 25;
        double p = 0.4;

        List<Double> vList = new ArrayList<>();
        List<Double> qList = new ArrayList<>();

        double prob = Math.pow(1 - p, n);
        double cdf = prob;

        vList.add(1.0 - cdf);
        qList.add(0.0);

        for (int x = 1; x <= n; x++) {
            prob = prob * (n - x) / (x + 1.0) * p / (1.0 - p);
            cdf += prob;

            double v = 1.0 - cdf;
            vList.add(v);
            qList.add(x / 10.0);
        }

        Collections.reverse(vList);
        Collections.reverse(qList);

        List<Double> cleanV = new ArrayList<>();
        List<Double> cleanQ = new ArrayList<>();
        double lastV = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < vList.size(); i++) {
            double v = vList.get(i);
            if (i == 0 || v > lastV + 1e-5) {
                cleanV.add(v);
                cleanQ.add(qList.get(i));
                lastV = v;
            }
        }

        return new PWLData(
                cleanV.stream().mapToDouble(d -> d).toArray(),
                cleanQ.stream().mapToDouble(d -> d).toArray()
        );
    }

    // ---------------- deep copy (local) ----------------

    private static Instance deepCopyInstance(Instance instance) {
        List<OperatingUnit> newOUs = new ArrayList<>();
        for (OperatingUnit ou : instance.operatingUnits) {
            OperatingUnit copy = new OperatingUnit(
                    ou.operatingUnitName,
                    ou.ouType,
                    ou.dailyFoodWaterKg,
                    ou.dailyFuelKg,
                    ou.dailyAmmoKg,
                    ou.maxFoodWaterKg,
                    ou.maxFuelKg,
                    ou.maxAmmoKg,
                    ou.source
            );
            copy.storageLevelFWKg = ou.storageLevelFWKg;
            copy.storageLevelFuelKg = ou.storageLevelFuelKg;
            copy.storageLevelAmmoKg = ou.storageLevelAmmoKg;
            newOUs.add(copy);
        }

        List<FSC> newFSCs = new ArrayList<>();
        for (FSC fsc : instance.FSCs) {
            Map<String, int[]> init = deepCopyIntArrayMap(fsc.initialStorageLevels);
            FSC fscCopy = new FSC(fsc.FSCname, fsc.maxStorageCapCcls, init);
            fscCopy.updateStorageLevels(deepCopyIntArrayMap(fsc.storageLevels));
            newFSCs.add(fscCopy);
        }

        Instance copy = new Instance(newOUs, newFSCs);
        copy.timeHorizon = instance.timeHorizon;
        return copy;
    }

    private static Map<String, int[]> deepCopyIntArrayMap(Map<String, int[]> map) {
        if (map == null) return null;
        Map<String, int[]> copy = new HashMap<>();
        for (Map.Entry<String, int[]> e : map.entrySet()) {
            int[] arr = e.getValue();
            if (arr == null) {
                copy.put(e.getKey(), null);
            } else {
                int[] arrCopy = new int[arr.length];
                System.arraycopy(arr, 0, arrCopy, 0, arr.length);
                copy.put(e.getKey(), arrCopy);
            }
        }
        return copy;
    }
}
