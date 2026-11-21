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
& (Join-Path $directory "prepare_runtime.ps1") -JavaHome $javaHomePath -MavenCommand $maven.Source -Force
& $maven.Source -f (Join-Path $directory "pom.xml") test
if ($LASTEXITCODE -ne 0) { throw "GUI unit tests failed with exit code $LASTEXITCODE." }
& (Join-Path $directory "src/test/powershell/PrepareRuntimeSmoke.ps1") `
    -PrepareScript (Join-Path $directory "prepare_runtime.ps1")

$classpath = (Join-Path $directory "target\test-classes") + ";" + `
    (Join-Path $directory "dist\seeker-gui-javafx.jar")
$launcherSmoke = Join-Path $directory "target\LauncherSnapshotSmoke.exe"
& $compiler /nologo /target:exe /main:LauncherSnapshotSmoke "/out:$launcherSmoke" `
    /reference:System.Windows.Forms.dll (Join-Path $directory "SeekerLauncher.cs") `
    (Join-Path $directory "src\test\csharp\LauncherSnapshotSmoke.cs")
if ($LASTEXITCODE -ne 0) {
    throw "Launcher snapshot check compilation failed with exit code $LASTEXITCODE."
}
& $launcherSmoke (Join-Path $directory "publish_jar.ps1")
if ($LASTEXITCODE -ne 0) {
    throw "Launcher snapshot check failed with exit code $LASTEXITCODE."
}
& (Join-Path $javaHomePath "bin\java.exe") -cp $classpath com.seeker.guifx.FatJarSmoke
if ($LASTEXITCODE -ne 0) {
    throw "Fat GUI jar isolation check failed with exit code $LASTEXITCODE."
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

if ($UiSmoke) {
    & $maven.Source -f (Join-Path $directory "pom.xml") '-Dtest=UiWindowTest' `
        '-Dseeker.gui.interactive=true' test
    if ($LASTEXITCODE -ne 0) { throw "Interactive GUI test failed with exit code $LASTEXITCODE." }
}

$source = Join-Path $directory "SeekerLauncher.cs"
$icon = Join-Path $directory "SeekerJobsIcon.ico"
$output = Join-Path $directory "SeekerJobs.exe"
$stagedLauncher = Join-Path $directory "target\SeekerJobs.exe"
& $compiler /nologo /target:winexe "/win32icon:$icon" "/out:$stagedLauncher" `
    /reference:System.Windows.Forms.dll $source
if ($LASTEXITCODE -ne 0) {
    throw "Launcher compilation failed with exit code $LASTEXITCODE."
}
if ([System.IO.File]::Exists($output)) {
    $previousLauncher = Join-Path $directory ("target\SeekerJobs-previous-" + [Guid]::NewGuid().ToString("N") + ".exe")
    [System.IO.File]::Replace($stagedLauncher, $output, $previousLauncher)
    try { [System.IO.File]::Delete($previousLauncher) }
    catch [System.IO.IOException] { }
    catch [System.UnauthorizedAccessException] { }
} else {
    [System.IO.File]::Move($stagedLauncher, $output)
}
[System.IO.File]::WriteAllText((Join-Path $directory "java-home.txt"), $javaHomePath)
Write-Output "Built $output"
