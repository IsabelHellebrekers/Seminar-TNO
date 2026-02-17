# Seminar-TNO

This project is a Java 17 + JavaFX + Gurobi project.

This README is terminal-first: all setup/build/run steps below are done from the command line.

## 1. Prerequisites

Install these first on your machine:

1. JDK 17 (exactly Java 17 is recommended).
2. Gurobi Optimizer 13.0.1 (or another 13.0.x version).
3. Maven 3.9+.
4. Git.

## 2. Install Java 17 (Terminal)

### Windows (PowerShell)

Install Temurin JDK 17:

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
```

Verify:

```powershell
java -version
javac -version
```

### macOS (Terminal)

Install Temurin JDK 17:

```bash
brew install --cask temurin@17
```

Verify:

```bash
java -version
javac -version
```

### Already have a newer Java version installed?

You do not need to uninstall it. Install JDK 17 as well, and use Java 17 for this project only.

Windows PowerShell (current terminal session):

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17..."
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -version
mvn -version
```

macOS/Linux (current shell session):

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
```

## 3. Install Maven (Terminal)

### Windows (PowerShell)

```powershell
winget install Apache.Maven
mvn -version
```

### macOS (Terminal)

```bash
brew install maven
mvn -version
```

## 4. Gurobi Setup (Most Important)

### Windows (PowerShell)

1. Install Gurobi (default usually `C:\gurobi1301\win64`).
2. Set machine-level environment variables:

```powershell
[System.Environment]::SetEnvironmentVariable("GUROBI_HOME","C:\gurobi1301\win64","Machine")
$path = [System.Environment]::GetEnvironmentVariable("Path","Machine")
$path = "$path;C:\gurobi1301\win64\bin;C:\gurobi1301\win64\lib"
[System.Environment]::SetEnvironmentVariable("Path",$path,"Machine")
```

3. Close and reopen PowerShell.
4. Activate license (example):

```powershell
grbgetkey <your-license-key>
```

5. Verify:

```powershell
echo $env:GUROBI_HOME
gurobi_cl --version
```

### macOS (MacBook Intel + Apple Silicon)

1. Install Gurobi Optimizer 13.0.1 for macOS from the Gurobi website.
2. Find your install path (example):
   - `/Library/gurobi1301/macos_universal2`
3. Add to shell profile (`~/.zshrc`):

```bash
export GUROBI_HOME="/Library/gurobi1301/macos_universal2"
export PATH="$GUROBI_HOME/bin:$PATH"
export DYLD_LIBRARY_PATH="$GUROBI_HOME/lib:$DYLD_LIBRARY_PATH"
```

4. Reload shell:

```bash
source ~/.zshrc
```

5. Set up license (academic/commercial), usually with:

```bash
grbgetkey <your-license-key>
```

6. Verify:

```bash
echo $GUROBI_HOME
gurobi_cl --version
```

### Linux

1. Install Gurobi.
2. Set:
   - `GUROBI_HOME=/path/to/gurobi/linux64`
3. Add to `PATH`:
   - `$GUROBI_HOME/bin`
4. Add to `LD_LIBRARY_PATH`:
   - `$GUROBI_HOME/lib`
5. Verify:

```bash
echo $GUROBI_HOME
gurobi_cl --version
```

## 5. Clone Project And Build (Terminal)

```bash
git clone <your-repo-url>
cd Seminar-TNO
mvn clean compile
```

## 6. Run The Project (Terminal)

### Run the JavaFX visualizer

```bash
mvn javafx:run
```

Main class used: `Visualisation.VisualiserApp`

### Run the normal Java main

```bash
mvn exec:java -Dexec.mainClass=Main
```

## 7. What Was Changed To Improve Portability

`pom.xml` no longer contains a machine-specific hardcoded path like `C:/gurobi1301/win64/lib/gurobi.jar`.

Instead:

1. It reads Gurobi from `GUROBI_HOME`.
2. It validates setup during Maven `validate` phase:
   - Fails early if `GUROBI_HOME` is missing.
   - Fails early if `${GUROBI_HOME}/lib/gurobi.jar` is missing.
3. JavaFX run uses `-Djava.library.path` based on `GUROBI_HOME`.

## 8. Troubleshooting

### Error: `GUROBI_HOME is not set`

Set `GUROBI_HOME`, close terminal/IDE, reopen, run `mvn clean compile` again.

### Error: `Could not find .../lib/gurobi.jar`

`GUROBI_HOME` points to wrong folder.  
It must be the Gurobi folder containing `lib/gurobi.jar` and `bin`.

### Error about missing Gurobi native library (`gurobi130.dll`, `.so`, `.dylib`)

Your OS library path is not configured correctly (`Path` / `LD_LIBRARY_PATH` / `DYLD_LIBRARY_PATH`).

### Build fails because `mvn` is unknown

Install Maven (`winget install Apache.Maven` or `brew install maven`) and open a new terminal.
