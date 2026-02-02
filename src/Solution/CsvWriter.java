package Solution;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class CsvWriter {

    private CsvWriter() {}

    public static void writeCsv(Path file, String[] header, List<String[]> rows) throws IOException {
        Files.createDirectories(file.getParent());

        try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            bw.write(toCsvLine(header));
            bw.newLine();
            for (String[] row : rows) {
                bw.write(toCsvLine(row));
                bw.newLine();
            }
        }
    }

    private static String toCsvLine(String[] fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(fields[i]));
        }
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        boolean mustQuote = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        if (!mustQuote) return s;

        String escaped = s.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
