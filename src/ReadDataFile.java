import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Pattern;

public class ReadDataFile {

    public static class OperatingUnit {
        public final String operatingUnit;

        public final long dailyFoodWaterKg;
        public final long dailyFuelKg;
        public final long dailyAmmoKg;

        public final long maxFoodWaterKg;
        public final long maxFuelKg;
        public final long maxAmmoKg;

        public final String source;              // e.g., MSC, FSC 1, FSC 2
        public final LocalTime orderTime;        // e.g., 18:00
        public final String timeWindow;          // e.g., 22:00-00:00
        public final int drivingTimeToSourceSec; // e.g., 3997

        public OperatingUnit(
                String operatingUnit,
                long dailyFoodWaterKg, long dailyFuelKg, long dailyAmmoKg,
                long maxFoodWaterKg, long maxFuelKg, long maxAmmoKg,
                String source, LocalTime orderTime, String timeWindow, int drivingTimeToSourceSec
        ) {
            this.operatingUnit = operatingUnit;
            this.dailyFoodWaterKg = dailyFoodWaterKg;
            this.dailyFuelKg = dailyFuelKg;
            this.dailyAmmoKg = dailyAmmoKg;
            this.maxFoodWaterKg = maxFoodWaterKg;
            this.maxFuelKg = maxFuelKg;
            this.maxAmmoKg = maxAmmoKg;
            this.source = source;
            this.orderTime = orderTime;
            this.timeWindow = timeWindow;
            this.drivingTimeToSourceSec = drivingTimeToSourceSec;
        }

        @Override public String toString() {
            return "OperatingUnitRow{" +
                    "operatingUnit='" + operatingUnit + '\'' +
                    ", daily(FW/Fuel/Ammo)=" + dailyFoodWaterKg + "/" + dailyFuelKg + "/" + dailyAmmoKg +
                    ", max(FW/Fuel/Ammo)=" + maxFoodWaterKg + "/" + maxFuelKg + "/" + maxAmmoKg +
                    ", source='" + source + '\'' +
                    ", orderTime=" + orderTime +
                    ", timeWindow='" + timeWindow + '\'' +
                    ", drivingTimeSec=" + drivingTimeToSourceSec +
                    '}';
        }
    }

    public static class SourceCapacityRow {
        public final String source;      // MSC, FSC 1, FSC 2
        public final Double maxFoodWaterKg; // Infinity allowed
        public final Double maxFuelKg;
        public final Double maxAmmoKg;
        public final Integer drivingTimeToSourceSec; // may be null/NA

        public SourceCapacityRow(String source, Double maxFoodWaterKg, Double maxFuelKg, Double maxAmmoKg, Integer drivingTimeToSourceSec) {
            this.source = source;
            this.maxFoodWaterKg = maxFoodWaterKg;
            this.maxFuelKg = maxFuelKg;
            this.maxAmmoKg = maxAmmoKg;
            this.drivingTimeToSourceSec = drivingTimeToSourceSec;
        }

        @Override public String toString() {
            return "SourceCapacityRow{" +
                    "source='" + source + '\'' +
                    ", max(FW/Fuel/Ammo)=" + maxFoodWaterKg + "/" + maxFuelKg + "/" + maxAmmoKg +
                    ", drivingTimeSec=" + drivingTimeToSourceSec +
                    '}';
        }
    }

    public static class CclContentRow {
        public final String type; // Type 1, Type 2, ...
        public final long foodWaterKg;
        public final long fuelKg;
        public final long ammoKg;

        public CclContentRow(String type, long foodWaterKg, long fuelKg, long ammoKg) {
            this.type = type;
            this.foodWaterKg = foodWaterKg;
            this.fuelKg = fuelKg;
            this.ammoKg = ammoKg;
        }

        @Override public String toString() {
            return "CclContentRow{" +
                    "type='" + type + '\'' +
                    ", FW=" + foodWaterKg +
                    ", Fuel=" + fuelKg +
                    ", Ammo=" + ammoKg +
                    '}';
        }
    }

    public static class ParsedData {
        public final List<OperatingUnit> operatingUnits = new ArrayList<>();
        public final Map<String, SourceCapacityRow> sourceCapacities = new LinkedHashMap<>();
        public final Map<String, CclContentRow> cclContents = new LinkedHashMap<>();
    }

    // ---------- Parser ----------
    private enum Section { NONE, OPERATING_UNITS, SOURCE_CAPACITIES, CCL_CONTENTS }

    // split on tabs OR 2+ spaces (to tolerate pasted text)
    private static final Pattern SPLIT_PATTERN = Pattern.compile("\\t+|\\s{2,}");

    public static ParsedData read(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        ParsedData data = new ParsedData();

        Section section = Section.NONE;

        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (line.isEmpty()) continue;

            // Detect section headers
            if (line.startsWith("Operating unit")) {
                section = Section.OPERATING_UNITS;
                continue; // skip header line
            }
            if (line.startsWith("CCLs content")) {
                section = Section.CCL_CONTENTS;
                continue;
            }
            // The second table doesn't have a clean title line in your paste,
            // but the real data rows start with "MSC" / "FSC 1" / "FSC 2"
            // and contain "infinity" or numbers in 3 capacity columns.
            if (looksLikeSourceCapacityRow(line)) {
                // If we were not already parsing it, switch to SOURCE_CAPACITIES.
                // This will also correctly parse MSC/FSC lines.
                section = Section.SOURCE_CAPACITIES;
            }
            if (line.startsWith("Type") && line.contains("Food&water") && line.contains("Ammunition")) {
                section = Section.CCL_CONTENTS;
                continue; // skip header line for CCL table
            }

            String[] parts = split(line);
            if (parts.length == 0) continue;

            switch (section) {
                case OPERATING_UNITS -> {
                    // Expect 11 columns:
                    // Operating unit | daily FW | daily Fuel | daily Ammo | max FW | max Fuel | max Ammo | Source | Order time | Time Window | Driving time
                    if (parts.length < 11) break; // ignore malformed lines

                    String operatingUnit = parts[0];

                    long dailyFW = parseLong(parts[1]);
                    long dailyFuel = parseLong(parts[2]);
                    long dailyAmmo = parseLong(parts[3]);

                    long maxFW = parseLong(parts[4]);
                    long maxFuel = parseLong(parts[5]);
                    long maxAmmo = parseLong(parts[6]);

                    String source = parts[7];
                    LocalTime orderTime = LocalTime.parse(parts[8]); // "18:00"
                    String timeWindow = parts[9];
                    int drivingSec = parseInt(parts[10]);

                    data.operatingUnits.add(new OperatingUnit(
                            operatingUnit,
                            dailyFW, dailyFuel, dailyAmmo,
                            maxFW, maxFuel, maxAmmo,
                            source, orderTime, timeWindow, drivingSec
                    ));
                }
                case SOURCE_CAPACITIES -> {
                    // Expect:
                    // Source | max FW | max Fuel | max Ammo | driving time (may be NA)
                    if (parts.length < 4) break;

                    String source = parts[0];
                    Double maxFW = parseDoubleOrInfinity(parts[1]);
                    Double maxFuel = parseDoubleOrInfinity(parts[2]);
                    Double maxAmmo = parseDoubleOrInfinity(parts[3]);

                    Integer drivingSec = null;
                    if (parts.length >= 5 && !parts[4].equalsIgnoreCase("NA")) {
                        drivingSec = parseInt(parts[4]);
                    }

                    data.sourceCapacities.put(source, new SourceCapacityRow(source, maxFW, maxFuel, maxAmmo, drivingSec));
                }
                case CCL_CONTENTS -> {
                    // Expect: Type | Food&water | Fuel | Ammunition
                    if (parts.length < 4) break;

                    String type = parts[0]; // "Type 1"
                    long fw = parseLong(parts[1]);
                    long fuel = parseLong(parts[2]);
                    long ammo = parseLong(parts[3]);

                    data.cclContents.put(type, new CclContentRow(type, fw, fuel, ammo));
                }
                default -> {
                    // ignore lines outside sections
                }
            }
        }

        return data;
    }

    private static boolean looksLikeSourceCapacityRow(String line) {
        // e.g. "MSC    infinity infinity infinity    NA"
        // or "FSC 1  132237 384717 752049 18334"
        String l = line.strip();
        return (l.startsWith("MSC") || l.startsWith("FSC")) && (l.toLowerCase().contains("infinity") || l.matches(".*\\d.*"));
    }

    private static String[] split(String line) {
        String[] parts = SPLIT_PATTERN.split(line.strip());
        // remove empty tokens
        List<String> cleaned = new ArrayList<>();
        for (String p : parts) {
            String c = p.strip();
            if (!c.isEmpty()) cleaned.add(c);
        }
        return cleaned.toArray(new String[0]);
    }

    private static long parseLong(String s) {
        return Long.parseLong(s.replace(",", "").trim());
    }

    private static int parseInt(String s) {
        return Integer.parseInt(s.replace(",", "").trim());
    }

    private static Double parseDoubleOrInfinity(String s) {
        String t = s.trim();
        if (t.equalsIgnoreCase("infinity") || t.equalsIgnoreCase("inf")) return Double.POSITIVE_INFINITY;
        return Double.valueOf(t.replace(",", ""));
    }

    public static void main(String[] args) throws Exception {
        Path path = Path.of("C:\\Eeke\\Erasmus universiteit\\master\\Data TNO.txt");
        ParsedData data = read(path);

        System.out.println("Operating units: " + data.operatingUnits.size());
        for (OperatingUnit row : data.operatingUnits) {
            System.out.println("  " + row);
        }

        System.out.println("\nSource capacities: " + data.sourceCapacities.size());
        for (SourceCapacityRow row : data.sourceCapacities.values()) {
            System.out.println("  " + row);
        }

        System.out.println("\nCCL contents: " + data.cclContents.size());
        for (CclContentRow row : data.cclContents.values()) {
            System.out.println("  " + row);
        }
    }
}
