import Objects.CCLpackage;
import Objects.Centre;
import Objects.OperatingUnit;
import Objects.instance;
import java.io.IOException;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws IOException {
        Path path = Path.of("src/Case/TNO_data_Erasmus_case.txt");
        instance inst = DataUtils.ReadDataFile.read(path);

        System.out.println("Operating units: " + inst.operatingUnits.size());
        for (OperatingUnit row : inst.operatingUnits) {
            System.out.println("  " + row);
        }

        System.out.println("\nSource capacities: " + inst.sourceCapacities.size());
        for (Centre row : inst.sourceCapacities.values()) {
            System.out.println("  " + row);
        }

        System.out.println("\nCCL contents: " + inst.cclContents.size());
        for (CCLpackage row : inst.cclContents.values()) {
            System.out.println("  " + row);
        }
    }   
}
