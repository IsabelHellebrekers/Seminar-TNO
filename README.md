# The Capacitated Resupply Problem

Seminar research project — Master Econometrics & Operations Research, Erasmus University Rotterdam, in collaboration with TNO.

## Case Description

The full problem description and data are provided by TNO and included in this repository:

- [Case description TNO.pdf](src/main/resources/Case/Case%20description%20TNO.pdf) — problem background, supply chain structure, and modelling assumptions
- [TNO_data_Erasmus_case.xlsx](src/main/resources/Case/TNO_data_Erasmus_case.xlsx) — instance data (demand, capacities, CCL compositions)

## Final Report


## Repository Structure

```
src/main/java/
├── Objects/              # Data model: Instance, FSC, OperatingUnit, CCLpackage, Result
├── DataUtils/            # Instance construction and CSV output
├── Deterministic/        # MILP formulation (CapacitatedResupplyMILP)
├── Stochastic/           # Stochastic fleet sizing, heuristic evaluation, sensitivity analysis
├── Visualisation/        # JavaFX visualiser + headless JSON exporter for GitHub Pages
│   ├── model/            # Simulation events (DemandEvent, FSCDeliveryEvent, DeliveryEvent)
│   ├── ui/               # GraphPane, FscPane, OperatingUnitPane, ControlsPane
│   └── util/             # Layout engine
└── Main.java             # Entry point for experiments
docs/
├── index.html            # Static web visualiser (GitHub Pages)
├── sim_fd.json           # Pre-computed FD simulation frames
├── sim_dispersed.json    # Pre-computed Dispersed simulation frames
└── scenarios.json        # Scenario manifest
```

## Prerequisites

| Tool             | Version      |
| ---------------- | ------------ |
| JDK              | 17 (exactly) |
| Gurobi Optimizer | 13.0.x       |
| Maven            | 3.9+         |
| Git              | any          |

## Setup

### 1. Install JDK 17

**Windows (PowerShell)**

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
java -version
```

**macOS**

```bash
brew install --cask temurin@17
java -version
```

> If you already have a newer JDK installed, install 17 alongside it and point `JAVA_HOME` to it for this project only.

### 2. Install Maven

**Windows**

```powershell
winget install Apache.Maven
mvn -version
```

**macOS**

```bash
brew install maven
mvn -version
```

### 3. Install and configure Gurobi

1. Download **Gurobi 13.0.x** from [gurobi.com](https://www.gurobi.com) and install it.
2. Obtain an academic or commercial license and activate it:
   ```bash
   grbgetkey <your-license-key>
   ```
3. Set the `GUROBI_HOME` environment variable to the Gurobi installation folder (the one containing `lib/gurobi.jar` and `bin/`).

**Windows (PowerShell — persistent)**

```powershell
[System.Environment]::SetEnvironmentVariable("GUROBI_HOME","C:\gurobi1301\win64","Machine")
$path = [System.Environment]::GetEnvironmentVariable("Path","Machine")
[System.Environment]::SetEnvironmentVariable("Path","$path;C:\gurobi1301\win64\bin;C:\gurobi1301\win64\lib","Machine")
# Restart PowerShell, then verify:
echo $env:GUROBI_HOME
gurobi_cl --version
```

**macOS — add to `~/.zshrc`**

```bash
export GUROBI_HOME="/Library/gurobi1301/macos_universal2"
export PATH="$GUROBI_HOME/bin:$PATH"
export DYLD_LIBRARY_PATH="$GUROBI_HOME/lib:$DYLD_LIBRARY_PATH"
source ~/.zshrc
gurobi_cl --version
```

**Linux — add to `~/.bashrc`**

```bash
export GUROBI_HOME="/opt/gurobi1301/linux64"
export PATH="$GUROBI_HOME/bin:$PATH"
export LD_LIBRARY_PATH="$GUROBI_HOME/lib:$LD_LIBRARY_PATH"
```

### 4. Clone and build

```bash
git clone https://github.com/IsabelHellebrekers/Seminar-TNO.git
cd Seminar-TNO
mvn clean compile
```

Maven reads `GUROBI_HOME` automatically and will fail early with a clear message if it is not set or points to the wrong folder.

## Running

### Experiments (`Main.java`)

```bash
mvn exec:java -Dexec.mainClass=Main
```

The `main` method in `Main.java` contains several experiment blocks — uncomment the ones you want to run:

| Block                                           | Description                                                            |
| ----------------------------------------------- | ---------------------------------------------------------------------- |
| `CapacitatedResupplyMILP.solveInstances(...)` | Solve the deterministic MILP                                           |
| `runDispersedExperiments()`                   | Solve all 512 dispersed partitions →`DispersedConceptSolutions.csv` |
| `runStochasticExperiments(...)`               | Fleet sizing, weight tuning, CCL composition search, OOS evaluation    |
| `runPerfectHindsightExperiments()`            | Perfect hindsight benchmark                                            |
| `runSensitivityAnalysis(...)`                 | Sensitivity on correlation and demand distributions                    |

### JavaFX visualiser

```bash
mvn javafx:run
```

Opens an animated visualisation of the supply chain for both the FD and Dispersed concepts. Controls: Play/Pause, Prev/Next step, speed slider, Debug mode (shows exact truck counts and inventory values).

Running the visualiser also re-exports `docs/sim_fd.json`, `docs/sim_dispersed.json`, and `docs/scenarios.json` for the web viewer.

## Web Visualiser (GitHub Pages)

A static version of the visualiser is hosted at:
**https://isabelhellebrekers.github.io/Seminar-TNO/**

Switch between scenarios using the dropdown in the top-left. Debug mode shows exact inventory levels and truck counts per arc.

The JSON files under `docs/` are generated by `SimExporter` when the JavaFX app runs. To update the web viewer after changing the model, run `mvn javafx:run` and commit the updated `docs/` files.
