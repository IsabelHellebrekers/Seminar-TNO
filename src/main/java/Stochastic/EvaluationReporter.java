package Stochastic;

import Objects.*;
import java.util.*;
import java.lang.reflect.Field;

/**
 * Diagnostic reporter for stockout analysis.
 * Simulates many scenarios and prints detailed statistics about stockout
 * frequency, severity, and distribution across products, days, and OUs.
 *
 * @author 621349it Ies Timmerarends
 * @author 612348ih Isabel Hellebrekers
 * @author 631426ls Lena Stiebing
 * @author 661267eb Eeke Bavelaar
 */
public class EvaluationReporter {

    private record StockoutRow(String ou, String product, double kg, int day) {
    }

    /**
     * Simulate nScenarios and print detailed stockout diagnostics to standard output.
     * Reports include per-product totals, percentiles, co-occurrence patterns,
     * and top OU/day breakdowns.
     *
     * @param data       the problem instance
     * @param M          number of trucks at the MSC
     * @param K          number of trucks at each FSC, keyed by FSC name
     * @param nScenarios number of scenarios to simulate
     * @param baseSeed   base seed; scenario s uses (baseSeed + s)
     * @param cfg        target / urgency weight configuration
     */
    public static void reportStockouts(
            Instance data,
            int M,
            Map<String, Integer> K,
            int nScenarios,
            long baseSeed,
            EvaluationHeuristic.WeightConfig cfg) {
        Set<String> ouNames = new HashSet<>();
        for (OperatingUnit ou : data.getOperatingUnits()) {
            ouNames.add(ou.getName());
        }

        List<Double> scenarioTotals = new ArrayList<>(nScenarios);
        int scenariosWithoutStockout = 0;
        double sumTotalStockoutKg = 0.0;

        Map<String, Double> totalKgByProduct = new LinkedHashMap<>();
        totalKgByProduct.put("FW", 0.0);
        totalKgByProduct.put("FUEL", 0.0);
        totalKgByProduct.put("AMMO", 0.0);

        Map<String, Integer> eventCountByProduct = new LinkedHashMap<>();
        eventCountByProduct.put("FW", 0);
        eventCountByProduct.put("FUEL", 0);
        eventCountByProduct.put("AMMO", 0);

        Map<String, Integer> scenarioCountByProduct = new LinkedHashMap<>();
        scenarioCountByProduct.put("FW", 0);
        scenarioCountByProduct.put("FUEL", 0);
        scenarioCountByProduct.put("AMMO", 0);

        Map<Integer, Double> totalKgByDay = new TreeMap<>();
        for (int t = 1; t <= data.getTimeHorizon(); t++) {
            totalKgByDay.put(t, 0.0);
        }

        Map<String, Double> totalKgByOu = new HashMap<>();
        Map<String, Double> totalKgByOuProduct = new HashMap<>();

        Map<Integer, Integer> firstStockoutDayHist = new TreeMap<>();
        for (int t = 1; t <= data.getTimeHorizon(); t++) {
            firstStockoutDayHist.put(t, 0);
        }

        Map<Integer, Integer> coOccurrence = new TreeMap<>();
        for (int mask = 0; mask <= 7; mask++) {
            coOccurrence.put(mask, 0);
        }

        for (int s = 1; s <= nScenarios; s++) {
            EvaluationHeuristic.ScenarioResult res = EvaluationHeuristic.evaluateSingleScenario(data, M, K,
                    baseSeed + s, cfg, List.of(0.0, 0.0, 0.0, 0.0)); // no correlation

            sumTotalStockoutKg += res.getTotalStockoutKg();
            scenarioTotals.add(res.getTotalStockoutKg());

            if (!res.isHasStockout()) {
                scenariosWithoutStockout++;
                coOccurrence.merge(0, 1, Integer::sum);
                continue;
            }

            Set<String> productsThisScenario = new HashSet<>();
            int firstDay = Integer.MAX_VALUE;

            for (Object soObj : res.getStockouts()) {
                StockoutRow row = extractRow(soObj, ouNames);
                if (row == null) {
                    continue;
                }

                totalKgByProduct.merge(row.product, row.kg, Double::sum);
                eventCountByProduct.merge(row.product, 1, Integer::sum);
                productsThisScenario.add(row.product);

                totalKgByDay.merge(row.day, row.kg, Double::sum);

                totalKgByOu.merge(row.ou, row.kg, Double::sum);

                String key = row.ou + " | " + row.product;
                totalKgByOuProduct.merge(key, row.kg, Double::sum);

                if (row.day < firstDay) {
                    firstDay = row.day;
                }
            }

            for (String p : productsThisScenario) {
                if (scenarioCountByProduct.containsKey(p)) {
                    scenarioCountByProduct.merge(p, 1, Integer::sum);
                }
            }

            int mask = 0;
            if (productsThisScenario.contains("FW")) {
                mask |= 1;
            }
            if (productsThisScenario.contains("FUEL")) {
                mask |= 2;
            }
            if (productsThisScenario.contains("AMMO")) {
                mask |= 4;
            }
            coOccurrence.merge(mask, 1, Integer::sum);

            if (firstDay != Integer.MAX_VALUE && firstStockoutDayHist.containsKey(firstDay)) {
                firstStockoutDayHist.merge(firstDay, 1, Integer::sum);
            }
        }

        double noStockoutPct = (double) scenariosWithoutStockout / nScenarios * 100.0;
        double avgStockoutKg = sumTotalStockoutKg / nScenarios;

        System.out.println("=== Stockout Diagnostics (WEIGHT-AWARE) ===");
        System.out.println("Using cfg: OU=" + cfg.ou() + " | VUST=" + cfg.vust());
        System.out.println("Total scenarios: " + nScenarios);
        System.out.println("Scenarios without stockout: " + scenariosWithoutStockout + " ("
                + String.format("%.2f", noStockoutPct) + "%)");
        System.out.println("Avg total stockout kg per scenario: " + String.format("%.2f", avgStockoutKg));
        System.out.println();

        System.out.println("By product (total kg | events | scenarios-with-product-stockout | avg kg per event):");
        for (String p : List.of("FW", "FUEL", "AMMO")) {
            double totKg = totalKgByProduct.getOrDefault(p, 0.0);
            int ev = eventCountByProduct.getOrDefault(p, 0);
            int sc = scenarioCountByProduct.getOrDefault(p, 0);
            double avgPerEvent = ev == 0 ? 0.0 : (totKg / ev);
            System.out.println("  " + p + ": " + String.format("%.2f", totKg)
                    + " | " + ev
                    + " | " + sc
                    + " | " + String.format("%.2f", avgPerEvent));
        }
        System.out.println();

        System.out.println("Scenario total stockout percentiles (kg):");
        printPercentiles(scenarioTotals, new int[] { 50, 75, 90, 95, 99 });
        System.out.println();

        System.out.println("First stockout day histogram (count of scenarios):");
        for (var e : firstStockoutDayHist.entrySet()) {
            if (e.getValue() > 0) {
                System.out.println("  Day " + e.getKey() + ": " + e.getValue());
            }
        }
        System.out.println();

        System.out.println("Co-occurrence of stockouts by product set (count of scenarios):");
        for (int mask : coOccurrence.keySet()) {
            System.out.println("  " + maskToLabel(mask) + ": " + coOccurrence.get(mask));
        }
        System.out.println();

        System.out.println("Top days by total stockout kg:");
        totalKgByDay.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(10)
                .forEach(e -> System.out.println("  Day " + e.getKey() + ": " + String.format("%.2f", e.getValue())));
        System.out.println();

        System.out.println("Top OUs by total stockout kg:");
        totalKgByOu.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(10)
                .forEach(e -> System.out.println("  " + e.getKey() + ": " + String.format("%.2f", e.getValue())));
        System.out.println();

        System.out.println("Top OU|Product pairs by total stockout kg:");
        totalKgByOuProduct.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(15)
                .forEach(e -> System.out.println("  " + e.getKey() + ": " + String.format("%.2f", e.getValue())));
        System.out.println();
    }

    private static void printPercentiles(List<Double> values, int[] ps) {
        if (values == null || values.isEmpty()) {
            System.out.println("  (no data)");
            return;
        }
        List<Double> v = new ArrayList<>(values);
        v.sort(Double::compareTo);

        for (int p : ps) {
            double x = percentile(v, p);
            System.out.println("  P" + p + ": " + String.format("%.2f", x));
        }
    }

    private static double percentile(List<Double> sorted, int p) {
        int n = sorted.size();
        if (n == 1) {
            return sorted.get(0);
        }

        double rank = (p / 100.0) * (n - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);

        if (lo == hi) {
            return sorted.get(lo);
        }
        double w = rank - lo;
        return sorted.get(lo) * (1.0 - w) + sorted.get(hi) * w;
    }

    private static String maskToLabel(int mask) {
        if (mask == 0) {
            return "None";
        }
        List<String> parts = new ArrayList<>();
        if ((mask & 1) != 0) {
            parts.add("FW");
        }
        if ((mask & 2) != 0) {
            parts.add("FUEL");
        }
        if ((mask & 4) != 0) {
            parts.add("AMMO");
        }
        return String.join("+", parts);
    }

    private static StockoutRow extractRow(Object soObj, Set<String> ouNames) {
        if (soObj == null) {
            return null;
        }

        String ou = null;
        String product = null;
        Double kg = null;
        Integer day = null;

        try {
            for (Field f : soObj.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object v = f.get(soObj);

                if (v instanceof String sv) {
                    if (ou == null && ouNames.contains(sv)) {
                        ou = sv;
                    }
                    if (product == null && (sv.equals("FW") || sv.equals("FUEL") || sv.equals("AMMO"))) {
                        product = sv;
                    }
                } else if (v instanceof Integer iv) {
                    if (day == null) {
                        day = iv;
                    }
                } else if (v instanceof Double dv) {
                    if (kg == null) {
                        kg = dv;
                    }
                } else if (v instanceof Number nv) {
                    if (kg == null) {
                        kg = nv.doubleValue();
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }

        if (ou == null || product == null || kg == null || day == null) {
            return null;
        }
        return new StockoutRow(ou, product, kg, day);
    }
}