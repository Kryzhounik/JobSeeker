$ErrorActionPreference = "Stop"

$guiDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$sourcePath = Join-Path $guiDirectory "JobsViewerLauncher.cs"
$iconSourcePath = Join-Path $guiDirectory "JobsViewerIcon.png"
$iconPath = Join-Path $guiDirectory "JobsViewerIcon.ico"
$outputPath = Join-Path $guiDirectory "JobsViewer.exe"
$compiler = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"

foreach ($requiredPath in @($sourcePath, $iconSourcePath, $compiler)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) {
        throw "Required file not found: $requiredPath"
    }
}

Add-Type -AssemblyName System.Drawing

$sizes = @(16, 20, 24, 32, 40, 48, 64, 128, 256)
$sourceImage = [System.Drawing.Image]::FromFile($iconSourcePath)
$iconImages = [System.Collections.Generic.List[byte[]]]::new()

try {
    foreach ($size in $sizes) {
        $bitmap = [System.Drawing.Bitmap]::new(
            $size,
            $size,
            [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
        )
        try {
            $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
            try {
                $graphics.Clear([System.Drawing.Color]::Transparent)
                $graphics.CompositingMode =
                    [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
                $graphics.CompositingQuality =
                    [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
                $graphics.InterpolationMode =
                    [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.SmoothingMode =
                    [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
                $graphics.PixelOffsetMode =
                    [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $graphics.DrawImage($sourceImage, 0, 0, $size, $size)
            }
            finally {
                $graphics.Dispose()
            }

            $stream = [System.IO.MemoryStream]::new()
            try {
                $bitmap.Save($stream, [System.Drawing.Imaging.ImageFormat]::Png)
                $iconImages.Add($stream.ToArray())
            }
            finally {
                $stream.Dispose()
            }
        }
        finally {
            $bitmap.Dispose()
        }
    }
}
finally {
    $sourceImage.Dispose()
}

$fileStream = [System.IO.File]::Create($iconPath)
$writer = [System.IO.BinaryWriter]::new($fileStream)
try {
    $writer.Write([uint16]0)
    $writer.Write([uint16]1)
    $writer.Write([uint16]$sizes.Count)

    $offset = 6 + (16 * $sizes.Count)
    for ($index = 0; $index -lt $sizes.Count; $index++) {
        $size = $sizes[$index]
        $imageBytes = $iconImages[$index]
        $writer.Write([byte]$(if ($size -eq 256) { 0 } else { $size }))
        $writer.Write([byte]$(if ($size -eq 256) { 0 } else { $size }))
        $writer.Write([byte]0)
        $writer.Write([byte]0)
        $writer.Write([uint16]1)
        $writer.Write([uint16]32)
        $writer.Write([uint32]$imageBytes.Length)
        $writer.Write([uint32]$offset)
        $offset += $imageBytes.Length
    }

    foreach ($imageBytes in $iconImages) {
        $writer.Write($imageBytes)
    }
}
finally {
    $writer.Dispose()
    $fileStream.Dispose()
}

& $compiler `
    /nologo `
    /target:winexe `
    "/win32icon:$iconPath" `
    "/out:$outputPath" `
    /reference:System.Windows.Forms.dll `
    $sourcePath

if ($LASTEXITCODE -ne 0) {
    throw "Launcher compilation failed with exit code $LASTEXITCODE."
}

Write-Output "Built $outputPath"
Write-Output "Embedded icon $iconPath"
