param(
    [Parameter(Mandatory = $true)][string]$SourcePath,
    [Parameter(Mandatory = $true)][string]$DestinationPath
)

$ErrorActionPreference = "Stop"
$source = (Resolve-Path -LiteralPath $SourcePath).Path
$destination = [System.IO.Path]::GetFullPath($DestinationPath)
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($destination)) | Out-Null
$staging = $destination + "." + [Guid]::NewGuid().ToString("N") + ".tmp"
try {
    [System.IO.File]::Copy($source, $staging)
    if ([System.IO.File]::Exists($destination)) {
        [System.IO.File]::Replace($staging, $destination, [NullString]::Value)
    } else {
        [System.IO.File]::Move($staging, $destination)
    }
} finally {
    if ([System.IO.File]::Exists($staging)) {
        try { [System.IO.File]::Delete($staging) }
        catch [System.IO.IOException] { Write-Warning "Staging file could not be removed: $staging" }
        catch [System.UnauthorizedAccessException] { Write-Warning "Staging file could not be removed: $staging" }
    }
}
