package DataUtils;

import Objects.Result;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Class that writes the results of solved instances to a CSV file.
 */
public class OutputCreator {

    /**
     * Writes the results of solved instances to a  CSV file. 
     * The CSV contains instance names, optimal truck vectors, and lists of operating units
     * supplied by each FSC. The output file is created as "DispersedConceptSolutions.csv".
     * @param results list of Result objects to write to the CSV file
     */
    public void createCSV(List<Result> results) {
        Path outputPath = Path.of("DispersedConceptSolutions.csv");

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {

            // Header
            StringBuilder header = new StringBuilder();
            header.append("Instance,ObjectiveTruckVector");
            for (int f = 1; f <= 10; f++) {
                header.append(",FSC_").append(f);
            }
            writer.write(header.toString());
            writer.newLine();

            // Rows
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

    /**
     * Converts a list of strings into JSON array format.
     * @param items list of strings to convert
     * @return JSON array string representation
     */
    private static String jsonArrayOfStrings(List<String> items) {
        return "[" + String.join(",", items) + "]";
    }

    /**
     * Converts a list of integers into JSON array format. 
     * @param items list of integers to convert
     * @return JSON array string representation
     */
    private static String jsonArrayOfIntegers(List<Integer> items) {
        return "[" + items.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")) + "]";
    }
}
