package DataUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Pattern;
import Objects.OperatingUnit;
import Objects.Centre;
import Objects.CCLpackage;
import Objects.instance;

public class ReadDataFile {
    // ---------- Parser ----------
    private enum Section { NONE, OPERATING_UNITS, SOURCE_CAPACITIES, CCL_CONTENTS }

    // split on tabs OR 2+ spaces (to tolerate pasted text)
    private static final Pattern SPLIT_PATTERN = Pattern.compile("\\t+|\\s{2,}");

    public static instance read(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        instance data = new instance();

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
            if (isCentre(line)) {
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

                    data.sourceCapacities.put(source, new Centre(source, maxFW, maxFuel, maxAmmo, drivingSec));
                }
                case CCL_CONTENTS -> {
                    // Expect: Type | Food&water | Fuel | Ammunition
                    if (parts.length < 4) break;

                    String type = parts[0]; // "Type 1"
                    long fw = parseLong(parts[1]);
                    long fuel = parseLong(parts[2]);
                    long ammo = parseLong(parts[3]);

                    data.cclContents.put(type, new CCLpackage(type, fw, fuel, ammo));
                }
                default -> {
                    // ignore lines outside sections
                }
            }
        }

        return data;
    }

    private static boolean isCentre(String line) {
        String l = line.strip();
        return (l.startsWith("MSC") || l.startsWith("FSC"));
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
}
