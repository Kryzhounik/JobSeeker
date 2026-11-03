param([Parameter(Mandatory = $true)][string]$PrepareScript)
$ErrorActionPreference = "Stop"
$fixture = Join-Path $env:TEMP ("SeekerPrepareSmoke-" + [Guid]::NewGuid().ToString("N"))
$gui = Join-Path $fixture "GUI/JavaFX"
$oldJava = $env:JAVA_HOME
$oldPath = $env:Path
try {
    foreach ($path in @("GUI/JavaFX/target", "GUI/JavaFX/dist", "Driver", "Backend/src/app/java")) {
        [IO.Directory]::CreateDirectory((Join-Path $fixture $path)) | Out-Null
    }
    [IO.File]::Copy($PrepareScript, (Join-Path $gui "prepare_runtime.ps1"))
    foreach ($path in @("GUI/JavaFX/pom.xml", "Backend/pom.xml")) {
        Set-Content (Join-Path $fixture $path) '<project />'
    }
    Set-Content (Join-Path $gui "publish_jar.ps1") "# publisher"
    Set-Content (Join-Path $fixture "Driver/pom.xml") '<project><modules><module>../Backend</module></modules></project>'
    $maven = Join-Path $fixture "maven.ps1"
    Set-Content $maven @'
Add-Content (Join-Path $PSScriptRoot "calls.txt") ($args -join " ")
$global:LASTEXITCODE = 0
if ($args -contains "package") {
    $gui = Join-Path $PSScriptRoot "GUI/JavaFX"
    $target = Join-Path $gui "target/seeker-gui-javafx.jar"
    Set-Content $target "new GUI"
    [IO.File]::Copy($target, (Join-Path $gui "dist/seeker-gui-javafx.jar"), $true)
}
'@
    $script = Join-Path $gui "prepare_runtime.ps1"
    & $script -JavaHome $fixture -MavenCommand $maven
    & $script -JavaHome $fixture -MavenCommand $maven
    if (@(Get-Content (Join-Path $fixture "calls.txt")).Count -ne 2) { throw "Unchanged startup rebuilt GUI" }
    Set-Content (Join-Path $fixture "Backend/src/app/java/New.java") "changed backend"
    & $script -JavaHome $fixture -MavenCommand $maven
    if (@(Get-Content (Join-Path $fixture "calls.txt")).Count -ne 4) { throw "Backend change was missed" }
    Set-Content (Join-Path $fixture "Backend/backend.jar") "new backend artifact"
    & $script -JavaHome $fixture -MavenCommand $maven
    if (@(Get-Content (Join-Path $fixture "calls.txt")).Count -ne 6) { throw "New dependency jar was missed" }
    (Get-Item (Join-Path $gui "target/seeker-gui-javafx.jar")).LastWriteTimeUtc = [DateTime]::UtcNow
    Set-Content $maven '$global:LASTEXITCODE = 1'
    $failed = $false
    try { & $script -JavaHome $fixture -MavenCommand $maven } catch { $failed = $true }
    if (-not $failed) { throw "Failed build was accepted" }
    Write-Output "Runtime preparation: unchanged startup, newer sources/artifacts and failure OK"
} finally {
    $env:JAVA_HOME = $oldJava
    $env:Path = $oldPath
    $resolved = [IO.Path]::GetFullPath($fixture)
    if ($resolved.StartsWith([IO.Path]::GetFullPath($env:TEMP), [StringComparison]::OrdinalIgnoreCase) -and
            (Split-Path -Leaf $resolved).StartsWith("SeekerPrepareSmoke-")) {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
