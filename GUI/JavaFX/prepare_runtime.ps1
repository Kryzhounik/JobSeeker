param([string]$JavaHome, [string]$MavenCommand, [switch]$Force)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$driverPom = Join-Path $root "Driver/pom.xml"
$jar = Join-Path $PSScriptRoot "dist/seeker-gui-javafx.jar"
[xml]$driver = Get-Content -LiteralPath $driverPom -Raw
$modules = @((Split-Path $driverPom), $PSScriptRoot) + @(
    $driver.project.modules.module | ForEach-Object {
        (Resolve-Path (Join-Path (Split-Path $driverPom) $_)).Path
    }
)
$inputs = @(
    foreach ($module in $modules) {
        Get-Item -LiteralPath (Join-Path $module "pom.xml")
        Get-ChildItem -LiteralPath $module -Filter *.jar -File
        foreach ($part in @("src/main", "src/app")) {
            $source = Join-Path $module $part
            if (Test-Path -LiteralPath $source) {
                Get-Item -LiteralPath $source
                Get-ChildItem -LiteralPath $source -Recurse
            }
        }
    }
    Get-Item -LiteralPath $PSCommandPath, (Join-Path $PSScriptRoot "publish_jar.ps1")
    Get-Item (Join-Path $PSScriptRoot "target/seeker-gui-javafx.jar") -ErrorAction SilentlyContinue
    if (Test-Path (Join-Path $root ".mvn")) { Get-ChildItem (Join-Path $root ".mvn") -Recurse -File }
)
$published = Get-Item -LiteralPath $jar -ErrorAction SilentlyContinue
if (-not $Force -and $published -and
        -not ($inputs | Where-Object { $_.LastWriteTimeUtc -gt $published.LastWriteTimeUtc })) {
    Write-Output "GUI build is current."
    return
}
if (-not $JavaHome) { $JavaHome = (Get-Content (Join-Path $PSScriptRoot "java-home.txt") -Raw).Trim() }
$mavenFile = Join-Path $PSScriptRoot "maven-path.txt"
if (-not $MavenCommand -and (Test-Path $mavenFile)) { $MavenCommand = (Get-Content $mavenFile -Raw).Trim() }
if (-not $MavenCommand -or -not (Test-Path -LiteralPath $MavenCommand)) {
    $MavenCommand = (Get-Command mvn.cmd -ErrorAction Stop).Source
}
$env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
$env:Path = (Join-Path $env:JAVA_HOME "bin") + ";" + $env:Path
Write-Output "Updating GUI and backend dependencies..."
& $MavenCommand -B -f $driverPom -DskipTests '-Dmdep.skip=true' clean install
if ($LASTEXITCODE -ne 0) { throw "Backend build failed with exit code $LASTEXITCODE." }
& $MavenCommand -B -f (Join-Path $PSScriptRoot "pom.xml") -DskipTests clean package
if ($LASTEXITCODE -ne 0) { throw "GUI build failed with exit code $LASTEXITCODE." }
$target = Get-Item (Join-Path $PSScriptRoot "target/seeker-gui-javafx.jar")
$published = Get-Item -LiteralPath $jar
if ($target.Length -ne $published.Length -or $target.LastWriteTimeUtc -ne $published.LastWriteTimeUtc) {
    throw "GUI build was not published to dist."
}
[IO.File]::WriteAllText($mavenFile, (Resolve-Path -LiteralPath $MavenCommand).Path)
Write-Output "GUI build is ready."
