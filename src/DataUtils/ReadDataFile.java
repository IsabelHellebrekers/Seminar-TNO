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
    private enum Section {
        NONE,
        OPERATING_UNITS,
        CENTRES,
        CCL_CONTENTS,
        INITIAL_STORAGE_FSC1,
        INITIAL_STORAGE_FSC2
    }

    // split on tabs OR 2+ spaces
    private static final Pattern SPLIT_PATTERN = Pattern.compile("\\t+|\\s{2,}");

    public static instance read(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        instance data = new instance();

        Section section = Section.NONE;

        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (line.isEmpty()) continue;

            // Detect section headers like [OPERATING_UNITS]
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

            // Skip section headers inside tables
            if (line.startsWith("OperatingUnit")) continue;
            if (line.startsWith("Centre")) continue;
            if (line.startsWith("Type")) continue;

            String[] parts = split(line);
            if (parts.length == 0) continue;

            switch (section) {
                case OPERATING_UNITS -> {
                    if (parts.length < 11) break; // ignore malformed lines

                    String operatingUnit = parts[0];

                    int dailyFW = Integer.parseInt(parts[1]);
                    int dailyFuel = Integer.parseInt(parts[2]);
                    int dailyAmmo = Integer.parseInt(parts[3]);

                    int maxFW = Integer.parseInt(parts[4]);
                    int maxFuel = Integer.parseInt(parts[5]);
                    int maxAmmo = Integer.parseInt(parts[6]);

                    String source = parts[7];
                    LocalTime orderTime = LocalTime.parse(parts[8]); // "18:00"
                    String timeWindow = parts[9];
                    int drivingSec = Integer.parseInt(parts[10]);

                    data.operatingUnits.add(new OperatingUnit(
                            operatingUnit,
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
                    long fw = Integer.parseInt(parts[1]);
                    long fuel = Integer.parseInt(parts[2]);
                    long ammo = Integer.parseInt(parts[3]);

                    data.cclContents.put(type, new CCLpackage(type, fw, fuel, ammo));
                }
                case INITIAL_STORAGE_FSC1, INITIAL_STORAGE_FSC2 -> {
                    if (parts.length < 4) break;

                    String operatingUnit = parts[0];
                    int type1 = Integer.parseInt(parts[1]);
                    int type2 = Integer.parseInt(parts[2]);
                    int type3 = Integer.parseInt(parts[3]);

                    String centre = (section == Section.INITIAL_STORAGE_FSC1) ? "FSC 1" : "FSC 2";
                    data.initialStorageLevels.computeIfAbsent(centre, k -> new HashMap<>()).put(operatingUnit, new int[]{type1, type2, type3});
                }
                default -> {
                    // ignore lines outside section
                }
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
