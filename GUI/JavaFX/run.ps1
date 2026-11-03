param(
    [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    $savedHome = Join-Path $PSScriptRoot "java-home.txt"
    if (Test-Path -LiteralPath $savedHome) {
        $JavaHome = (Get-Content -LiteralPath $savedHome -Raw).Trim()
    }
}
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

$env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
$env:Path = (Join-Path $env:JAVA_HOME "bin") + ";" + $env:Path
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
& (Join-Path $PSScriptRoot "prepare_runtime.ps1") -JavaHome $env:JAVA_HOME
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
