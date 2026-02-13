package DataUtils;

import Objects.Result;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class OutputCreator {
    public void createCSV(List<Result> results) {
        Path outputPath = Path.of("DispersedConceptSolutions.csv");

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {

            // ===== Header =====
            StringBuilder header = new StringBuilder();
            header.append("Instance,ObjectiveTruckVector");
            for (int f = 1; f <= 10; f++) {
                header.append(",FSC_").append(f);
            }
            writer.write(header.toString());
            writer.newLine();

            // ===== Rows =====
            for (Result r : results) {
                StringBuilder row = new StringBuilder();

                // Instance name
                row.append(r.getInstanceName()).append(",");

                // Objective truck vector
                String truckVector = r.isOptimal()
                        ? jsonArrayOfIntegers(r.getTruckVector())
                        : "NA";
                row.append(truckVector);

                // FSC columns
                for (int f = 1; f <= 10; f++) {
                    row.append(",");
                    row.append(jsonArrayOfStrings(r.getOusSuppliedByFsc(f)));
                }

                writer.write(row.toString());
                writer.newLine();
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to write CSV file", e);
        }
    }

    // ===== Helpers =====
    private static String jsonArrayOfStrings(List<String> items) {
        return "[" + String.join(",", items) + "]";
    }

    private static String jsonArrayOfIntegers(List<Integer> items) {
        return "[" + items.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")) + "]";
    }
}
