param(
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$UiSmoke
)

$ErrorActionPreference = "Stop"
$directory = $PSScriptRoot
$root = (Resolve-Path -LiteralPath (Join-Path $directory "..\..")).Path
if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    throw "Set JAVA_HOME or pass -JavaHome pointing to JDK 21."
}
$javaHomePath = (Resolve-Path -LiteralPath $JavaHome).Path
$javaw = Join-Path $javaHomePath "bin\javaw.exe"
if (-not (Test-Path -LiteralPath $javaw -PathType Leaf)) {
    throw "Java runtime not found: $javaw"
}
$maven = Get-Command mvn.cmd -ErrorAction Stop
$compiler = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if (-not (Test-Path -LiteralPath $compiler -PathType Leaf)) {
    throw "C# compiler not found: $compiler"
}

$env:JAVA_HOME = $javaHomePath
$env:Path = (Join-Path $javaHomePath "bin") + ";" + $env:Path
& (Join-Path $directory "create_icon.ps1")
$pom = Join-Path $directory "pom.xml"
& $maven.Source -f (Join-Path $root "Driver\pom.xml") install
if ($LASTEXITCODE -ne 0) {
    throw "Backend Maven install failed with exit code $LASTEXITCODE."
}
& $maven.Source -f $pom clean package
if ($LASTEXITCODE -ne 0) {
    throw "Maven build failed with exit code $LASTEXITCODE."
}

$classpath = (Join-Path $directory "target\test-classes") + ";" + `
    (Join-Path $directory "seeker-gui-javafx.jar")
& (Join-Path $javaHomePath "bin\java.exe") -cp $classpath com.seeker.guifx.FatJarSmoke
if ($LASTEXITCODE -ne 0) {
    throw "Fat GUI jar isolation check failed with exit code $LASTEXITCODE."
}
& (Join-Path $javaHomePath "bin\java.exe") -cp $classpath com.seeker.guifx.ClientDataSmoke
if ($LASTEXITCODE -ne 0) {
    throw "JavaFX data connection check failed with exit code $LASTEXITCODE."
}
& (Join-Path $javaHomePath "bin\java.exe") -cp $classpath com.seeker.guifx.ClientDataContractsSmoke
if ($LASTEXITCODE -ne 0) {
    throw "Client data contract check failed with exit code $LASTEXITCODE."
}
& (Join-Path $javaHomePath "bin\java.exe") -cp $classpath com.seeker.guifx.GuiFeaturesSmoke
if ($LASTEXITCODE -ne 0) {
    throw "JavaFX feature check failed with exit code $LASTEXITCODE."
}
& (Join-Path $javaHomePath "bin\java.exe") -cp $classpath com.seeker.guifx.CollectorProgressSmoke
if ($LASTEXITCODE -ne 0) {
    throw "Collector progress check failed with exit code $LASTEXITCODE."
}
& (Join-Path $javaHomePath "bin\java.exe") -cp $classpath com.seeker.guifx.BackgroundProcessPaneSmoke
if ($LASTEXITCODE -ne 0) {
    throw "Background process panel check failed with exit code $LASTEXITCODE."
}
& (Join-Path $javaHomePath "bin\java.exe") -cp $classpath com.seeker.guifx.LinkedInAvailabilitySmoke
if ($LASTEXITCODE -ne 0) {
    throw "LinkedIn availability check failed with exit code $LASTEXITCODE."
}
& (Join-Path $javaHomePath "bin\java.exe") -cp $classpath com.seeker.guifx.JobSortRefreshSmoke
if ($LASTEXITCODE -ne 0) {
    throw "JavaFX job sorting check failed with exit code $LASTEXITCODE."
}
& (Join-Path $javaHomePath "bin\java.exe") -cp $classpath com.seeker.guifx.FxmlSmoke
if ($LASTEXITCODE -ne 0) {
    throw "JavaFX FXML check failed with exit code $LASTEXITCODE."
}
& (Join-Path $javaHomePath "bin\java.exe") -cp $classpath com.seeker.guifx.FillLastColumnResizePolicySmoke
if ($LASTEXITCODE -ne 0) {
    throw "Table column fill check failed with exit code $LASTEXITCODE."
}
if ($UiSmoke) {
    & (Join-Path $javaHomePath "bin\java.exe") -cp $classpath com.seeker.guifx.UiSmoke
    if ($LASTEXITCODE -ne 0) {
        throw "JavaFX UI check failed with exit code $LASTEXITCODE."
    }
}
Push-Location $root
try {
    & python -m unittest GUI.test_client_data_smoke
    if ($LASTEXITCODE -ne 0) {
        throw "Client data write check failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}

$source = Join-Path $directory "SeekerLauncher.cs"
$icon = Join-Path $directory "SeekerJobsIcon.ico"
$output = Join-Path $directory "SeekerJobs.exe"
try {
    $stream = [System.IO.File]::Open($output, 'OpenOrCreate', 'ReadWrite', 'None')
    $stream.Dispose()
} catch [System.IO.IOException] {
    $output = Join-Path $directory "SeekerJobs-updated.exe"
    Write-Output "SeekerJobs.exe is running; building $output instead."
}
& $compiler /nologo /target:winexe "/win32icon:$icon" "/out:$output" `
    /reference:System.Windows.Forms.dll $source
if ($LASTEXITCODE -ne 0) {
    throw "Launcher compilation failed with exit code $LASTEXITCODE."
}
[System.IO.File]::WriteAllText((Join-Path $directory "java-home.txt"), $javaHomePath)
Write-Output "Built $output"
