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
& $mavenCommand.Source -f $pom javafx:run
exit $LASTEXITCODE
