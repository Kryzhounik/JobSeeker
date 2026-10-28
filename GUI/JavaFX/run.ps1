param(
    [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($null -eq $javaCommand) {
        throw "Set JAVA_HOME to a JDK 21 installation or add its bin directory to PATH."
    }
    $JavaHome = Split-Path -Parent (Split-Path -Parent $javaCommand.Source)
}

$javaExecutable = Join-Path $JavaHome "bin\java.exe"
if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
    throw "JDK not found at '$JavaHome'. Expected '$javaExecutable'."
}

$mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($null -eq $mavenCommand) {
    throw "Maven 3.9 or newer must be available as mvn.cmd on PATH."
}

$env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
$env:Path = (Join-Path $env:JAVA_HOME "bin") + ";" + $env:Path
$pom = Join-Path $PSScriptRoot "pom.xml"
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
& $mavenCommand.Source -f (Join-Path $root "Driver\pom.xml") install
if ($LASTEXITCODE -ne 0) {
    throw "Backend Maven install failed with exit code $LASTEXITCODE."
}
& $mavenCommand.Source -f $pom clean package
if ($LASTEXITCODE -ne 0) {
    throw "JavaFX Maven build failed with exit code $LASTEXITCODE."
}
Push-Location $root
$runtimeDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("SeekerJobs\" + [Guid]::NewGuid().ToString("N"))
$runtimeJar = Join-Path $runtimeDirectory "seeker-gui-javafx.jar"
try {
    [System.IO.Directory]::CreateDirectory($runtimeDirectory) | Out-Null
    $inputJar = [System.IO.File]::Open((Join-Path $PSScriptRoot "dist\seeker-gui-javafx.jar"),
        'Open', 'Read', ([System.IO.FileShare]::ReadWrite -bor [System.IO.FileShare]::Delete))
    try {
        $outputJar = [System.IO.File]::Create($runtimeJar)
        try { $inputJar.CopyTo($outputJar) } finally { $outputJar.Dispose() }
    } finally { $inputJar.Dispose() }
    & $javaExecutable -jar $runtimeJar
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
    [System.IO.File]::Delete($runtimeJar)
    if ([System.IO.Directory]::Exists($runtimeDirectory)) {
        [System.IO.Directory]::Delete($runtimeDirectory)
    }
}
exit $exitCode
