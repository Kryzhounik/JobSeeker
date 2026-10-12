$ErrorActionPreference = "Stop"

$source = Join-Path $PSScriptRoot "pink_panther_transparent_final.ico"
$ico = Join-Path $PSScriptRoot "SeekerJobsIcon.ico"
$png = Join-Path $PSScriptRoot "src\main\resources\com\seeker\guifx\SeekerJobsIcon.png"
$bytes = [System.IO.File]::ReadAllBytes($source)
if ($bytes.Length -lt 22 -or [BitConverter]::ToUInt16($bytes, 2) -ne 1) {
    throw "Invalid icon source: $source"
}

$count = [BitConverter]::ToUInt16($bytes, 4)
$frame = $null
for ($index = 0; $index -lt $count; $index++) {
    $entry = 6 + 16 * $index
    if ($entry + 16 -gt $bytes.Length) { throw "Truncated icon source: $source" }
    if ($bytes[$entry] -ne 0 -or $bytes[$entry + 1] -ne 0) { continue }
    $size = [BitConverter]::ToUInt32($bytes, $entry + 8)
    $offset = [BitConverter]::ToUInt32($bytes, $entry + 12)
    if ([long]$offset + [long]$size -gt $bytes.Length) {
        throw "Invalid icon frame: $source"
    }
    $frame = [byte[]]::new($size)
    [Array]::Copy($bytes, $offset, $frame, 0, $size)
    break
}
if ($null -eq $frame -or $frame.Length -lt 8 -or
        [BitConverter]::ToString($frame, 0, 8) -ne "89-50-4E-47-0D-0A-1A-0A") {
    throw "A 256x256 PNG frame is required in $source"
}

[System.IO.File]::WriteAllBytes($png, $frame)
Copy-Item -LiteralPath $source -Destination $ico -Force
