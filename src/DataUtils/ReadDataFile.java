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
import Objects.OuType;

public class ReadDataFile {

    private enum Section {
        NONE,
        OPERATING_UNITS,
        CENTRES,
        CCL_CONTENTS,
        INITIAL_STORAGE_FSC1,
        INITIAL_STORAGE_FSC2
    }

    private static final Pattern SPLIT_PATTERN = Pattern.compile("\\t+|\\s{2,}");

    private static final Map<String, OuType> OU_TYPE_MAP = Map.ofEntries(
            Map.entry("Vust", OuType.VUST),

            Map.entry("Gn cie 1", OuType.GN_CIE),
            Map.entry("Gn cie 2", OuType.GN_CIE),

            Map.entry("Painf cie 1", OuType.PAINF_CIE),
            Map.entry("Painf cie 2", OuType.PAINF_CIE),
            Map.entry("Painf cie 3", OuType.PAINF_CIE),
            Map.entry("Painf cie 4", OuType.PAINF_CIE),
            Map.entry("Painf cie 5", OuType.PAINF_CIE),

            Map.entry("AT cie 1", OuType.AT_CIE),
            Map.entry("AT cie 2", OuType.AT_CIE),
            Map.entry("AT cie 3", OuType.AT_CIE)
    );

    private static final Map<String, OuType> INIT_STORAGE_KEY_MAP = Map.ofEntries(
        // Vust
        Map.entry("Vust", OuType.VUST),
        Map.entry("VUST", OuType.VUST),

        // AT
        Map.entry("AT", OuType.AT_CIE),
        Map.entry("AT cie", OuType.AT_CIE),
        Map.entry("AT cie 1", OuType.AT_CIE),
        Map.entry("AT cie 2", OuType.AT_CIE),
        Map.entry("AT cie 3", OuType.AT_CIE),

        // GN
        Map.entry("Gn", OuType.GN_CIE),
        Map.entry("GN", OuType.GN_CIE),
        Map.entry("Gn cie", OuType.GN_CIE),
        Map.entry("Gn cie 1", OuType.GN_CIE),
        Map.entry("Gn cie 2", OuType.GN_CIE),

        // PAINF
        Map.entry("Painf", OuType.PAINF_CIE),
        Map.entry("PAINF", OuType.PAINF_CIE),
        Map.entry("Painf cie", OuType.PAINF_CIE),
        Map.entry("Painf cie 1", OuType.PAINF_CIE),
        Map.entry("Painf cie 2", OuType.PAINF_CIE),
        Map.entry("Painf cie 3", OuType.PAINF_CIE),
        Map.entry("Painf cie 4", OuType.PAINF_CIE),
        Map.entry("Painf cie 5", OuType.PAINF_CIE)
    );


    public static instance read(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        instance data = new instance();

        Section section = Section.NONE;

        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (line.isEmpty()) continue;

            if (line.startsWith("[") && line.endsWith("]")) {
                String tag = line.substring(1, line.length() - 1).trim();
                section = switch (tag) {
                    case "OPERATING_UNITS" -> Section.OPERATING_UNITS;
                    case "CENTRES" -> Section.CENTRES;
                    case "CCL_CONTENTS_IN_KG" -> Section.CCL_CONTENTS;
                    case "INITIAL_STORAGE_LEVELS_FSC1" -> Section.INITIAL_STORAGE_FSC1;
                    case "INITIAL_STORAGE_LEVELS_FSC2" -> Section.INITIAL_STORAGE_FSC2;
                    default -> Section.NONE;
                };
                continue;
            }

            if (line.startsWith("OperatingUnit")) continue;
            if (line.startsWith("Centre")) continue;
            if (line.startsWith("Type")) continue;

            String[] parts = split(line);
            if (parts.length == 0) continue;

            switch (section) {
                case OPERATING_UNITS -> {
                    if (parts.length < 11) break;

                    String operatingUnit = parts[0];

                    long dailyFW = Long.parseLong(parts[1]);
                    long dailyFuel = Long.parseLong(parts[2]);
                    long dailyAmmo = Long.parseLong(parts[3]);

                    long maxFW = Long.parseLong(parts[4]);
                    long maxFuel = Long.parseLong(parts[5]);
                    long maxAmmo = Long.parseLong(parts[6]);

                    String source = parts[7];
                    LocalTime orderTime = LocalTime.parse(parts[8]);
                    String timeWindow = parts[9];
                    int drivingSec = Integer.parseInt(parts[10]);

                    OuType ouType = OU_TYPE_MAP.get(operatingUnit);
                    if (ouType == null) {
                        throw new IllegalArgumentException("No OuType defined for OperatingUnit: " + operatingUnit);
                    }

                    data.operatingUnits.add(new OperatingUnit(
                            operatingUnit,
                            ouType,
                            dailyFW, dailyFuel, dailyAmmo,
                            maxFW, maxFuel, maxAmmo,
                            source, orderTime, timeWindow, drivingSec
                    ));
                }
                case CENTRES -> {
                    if (parts.length < 2) break;

                    String centre = parts[0];
                    Integer maxStorageCapCcls = parseIntOrNull(parts[1]);
                    String source = (parts.length >= 3) ? parseNullableString(parts[2]) : null;
                    LocalTime orderTime = (parts.length >= 4) ? parseTimeOrNull(parts[3]) : null;
                    String timeWindow = (parts.length >= 5) ? parseNullableString(parts[4]) : null;
                    Integer dtSourceSec = (parts.length >= 6) ? parseIntOrNull(parts[5]) : null;

                    data.sourceCapacities.put(centre, new Centre(
                            centre, maxStorageCapCcls, source, orderTime, timeWindow, dtSourceSec
                    ));
                }
                case CCL_CONTENTS -> {
                    if (parts.length < 4) break;

                    String type = parts[0];
                    long fw = Long.parseLong(parts[1]);
                    long fuel = Long.parseLong(parts[2]);
                    long ammo = Long.parseLong(parts[3]);

                    data.cclContents.put(type, new CCLpackage(type, fw, fuel, ammo));
                }
                case INITIAL_STORAGE_FSC1, INITIAL_STORAGE_FSC2 -> {
                    if (parts.length < 4) break;

                    String key = parts[0];
                    int type1 = Integer.parseInt(parts[1]);
                    int type2 = Integer.parseInt(parts[2]);
                    int type3 = Integer.parseInt(parts[3]);

                    String centre = (section == Section.INITIAL_STORAGE_FSC1) ? "FSC 1" : "FSC 2";

                    OuType ouType = INIT_STORAGE_KEY_MAP.get(key);
                    if (ouType == null) {
                        ouType = OU_TYPE_MAP.get(key);
                    }
                    if (ouType == null) {
                        throw new IllegalArgumentException("Unknown initial storage key '" + key + "' in " + centre);
                    }

                    data.initialStorageLevels
                            .computeIfAbsent(centre, k -> new HashMap<>())
                            .put(ouType.name(), new int[]{type1, type2, type3});
                }
                default -> { }
            }
        }

        return data;
    }

    private static String[] split(String line) {
        String[] parts = SPLIT_PATTERN.split(line.strip());
        List<String> cleaned = new ArrayList<>();
        for (String p : parts) {
            String c = p.strip();
            if (!c.isEmpty()) cleaned.add(c);
        }
        return cleaned.toArray(new String[0]);
    }

    private static Integer parseIntOrNull(String s) {
        String t = s.trim();
        if (t.equals("-")) return null;
        return Integer.valueOf(t);
    }

    private static String parseNullableString(String s) {
        String t = s.trim();
        if (t.equals("-")) return null;
        return t;
    }

    private static LocalTime parseTimeOrNull(String s) {
        String t = s.trim();
        if (t.equals("-")) return null;
        return LocalTime.parse(t);
    }
}
