// ...existing code...
package Stochastic;

import Objects.*;
import java.util.*;
import java.lang.reflect.Field;

public class EvaluationReporter {

    public static void reportStockouts(Instance data, int M, Map<String,Integer> K, int nScenarios, long baseSeed) {
        Map<String,Integer> productCounts = new HashMap<>();
        productCounts.put("FW", 0);
        productCounts.put("FUEL", 0);
        productCounts.put("AMMO", 0);

        Map<String,Integer> ouCounts = new LinkedHashMap<>();
        for (OperatingUnit ou : data.operatingUnits) {
            ouCounts.put(ou.operatingUnitName, 0);
        }

        Set<String> productSet = new HashSet<>(Arrays.asList("FW","FUEL","AMMO"));
        Set<String> ouNames = new HashSet<>(ouCounts.keySet());

        int scenariosWithoutStockout = 0;

        for (int s = 1; s <= nScenarios; s++) {
            EvaluationHeuristic.ScenarioResult res = EvaluationHeuristic.evaluateSingleScenario(data, M, K, baseSeed + s);
            if (!res.hasStockout) {
                scenariosWithoutStockout++;
                continue;
            }

            // per-scenario sets to avoid double counting across days within same scenario
            Set<String> productsThisScenario = new HashSet<>();
            Set<String> ousThisScenario = new HashSet<>();

            for (Object so : res.stockouts) {
                String prod = null;
                String ouName = null;
                try {
                    for (Field f : so.getClass().getDeclaredFields()) {
                        f.setAccessible(true);
                        Object v = f.get(so);
                        if (v instanceof String) {
                            String sv = (String) v;
                            if (productSet.contains(sv)) prod = sv;
                            if (ouNames.contains(sv)) ouName = sv;
                        }
                    }
                } catch (Exception e) {
                    // ignore reflection failures for an entry
                }

                if (prod != null) productsThisScenario.add(prod);
                if (ouName != null) ousThisScenario.add(ouName);
            }

            for (String p : productsThisScenario) productCounts.merge(p, 1, Integer::sum);
            for (String o : ousThisScenario) ouCounts.merge(o, 1, Integer::sum);
        }

        System.out.println("Total scenarios : " + nScenarios);
        System.out.println("Scenarios without stockout : " + scenariosWithoutStockout);
        System.out.println();

        System.out.println("Stockout in FW : " + productCounts.get("FW") + " / " + nScenarios);
        System.out.println("Stockout in FUEL : " + productCounts.get("FUEL") + " / " + nScenarios);
        System.out.println("Stockout in AMMO : " + productCounts.get("AMMO") + " / " + nScenarios);
        System.out.println();
        System.out.println("Per OU : ");
        for (Map.Entry<String,Integer> e : ouCounts.entrySet()) {
            System.out.println("Stockout at " + e.getKey() + " : " + e.getValue() + " / " + nScenarios);
        }
    }
}