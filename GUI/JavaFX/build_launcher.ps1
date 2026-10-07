param(
    [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = "Stop"
$directory = $PSScriptRoot
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
$dependencies = Join-Path $directory "target\dependency"
& $maven.Source -f $pom package dependency:copy-dependencies "-DoutputDirectory=$dependencies"
if ($LASTEXITCODE -ne 0) {
    throw "Maven build failed with exit code $LASTEXITCODE."
}

$classpath = (Join-Path $directory "target\test-classes") + ";" + `
    (Join-Path $directory "target\classes") + ";" + (Join-Path $dependencies "*")
& (Join-Path $javaHomePath "bin\java.exe") -cp $classpath com.seeker.guifx.ClientDataSmoke
if ($LASTEXITCODE -ne 0) {
    throw "JavaFX data connection check failed with exit code $LASTEXITCODE."
}
& (Join-Path $javaHomePath "bin\java.exe") -cp $classpath com.seeker.guifx.GuiFeaturesSmoke
if ($LASTEXITCODE -ne 0) {
    throw "JavaFX feature check failed with exit code $LASTEXITCODE."
}
& (Join-Path $javaHomePath "bin\java.exe") -cp $classpath com.seeker.guifx.FxmlSmoke
if ($LASTEXITCODE -ne 0) {
    throw "JavaFX FXML check failed with exit code $LASTEXITCODE."
}
& (Join-Path $javaHomePath "bin\java.exe") -cp $classpath com.seeker.guifx.UiSmoke
if ($LASTEXITCODE -ne 0) {
    throw "JavaFX UI check failed with exit code $LASTEXITCODE."
}
& python -m unittest GUI.test_client_data_smoke
if ($LASTEXITCODE -ne 0) {
    throw "Client data write check failed with exit code $LASTEXITCODE."
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
