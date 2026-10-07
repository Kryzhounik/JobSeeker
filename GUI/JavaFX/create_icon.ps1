$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$png = Join-Path $PSScriptRoot "src\main\resources\com\seeker\guifx\SeekerJobsIcon.png"
$ico = Join-Path $PSScriptRoot "SeekerJobsIcon.ico"
$size = 256
$bitmap = [System.Drawing.Bitmap]::new($size, $size)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
try {
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $graphics.Clear([System.Drawing.Color]::FromArgb(7, 7, 12))
    $yellow = [System.Drawing.SolidBrush]::new(
        [System.Drawing.Color]::FromArgb(255, 218, 0))
    $pink = [System.Drawing.SolidBrush]::new(
        [System.Drawing.Color]::FromArgb(255, 45, 126))
    $font = [System.Drawing.Font]::new(
        "Segoe UI Black", 190, [System.Drawing.FontStyle]::Bold,
        [System.Drawing.GraphicsUnit]::Pixel)
    try {
        $glyph = "S"
        $bounds = $graphics.MeasureString($glyph, $font)
        $x = ($size - $bounds.Width) / 2
        $y = ($size - $bounds.Height) / 2 - 8
        $graphics.DrawString($glyph, $font, $yellow, $x, $y)
        $graphics.FillRectangle($pink, 164, 219, 56, 12)
    }
    finally {
        $font.Dispose()
        $yellow.Dispose()
        $pink.Dispose()
    }
    $bitmap.Save($png, [System.Drawing.Imaging.ImageFormat]::Png)
}
finally {
    $graphics.Dispose()
    $bitmap.Dispose()
}

$sizes = @(16, 24, 32, 48, 64, 128, 256)
$images = [System.Collections.Generic.List[byte[]]]::new()
$source = [System.Drawing.Image]::FromFile($png)
try {
    foreach ($edge in $sizes) {
        $scaled = [System.Drawing.Bitmap]::new($edge, $edge)
        $drawing = [System.Drawing.Graphics]::FromImage($scaled)
        try {
            $drawing.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
            $drawing.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $drawing.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $drawing.DrawImage($source, 0, 0, $edge, $edge)
            $stream = [System.IO.MemoryStream]::new()
            try {
                $scaled.Save($stream, [System.Drawing.Imaging.ImageFormat]::Png)
                $images.Add($stream.ToArray())
            }
            finally { $stream.Dispose() }
        }
        finally {
            $drawing.Dispose()
            $scaled.Dispose()
        }
    }
}
finally { $source.Dispose() }

$writer = [System.IO.BinaryWriter]::new([System.IO.File]::Create($ico))
try {
    $writer.Write([uint16]0)
    $writer.Write([uint16]1)
    $writer.Write([uint16]$sizes.Count)
    $offset = 6 + 16 * $sizes.Count
    for ($index = 0; $index -lt $sizes.Count; $index++) {
        $edge = $sizes[$index]
        $bytes = $images[$index]
        $writer.Write([byte]$(if ($edge -eq 256) { 0 } else { $edge }))
        $writer.Write([byte]$(if ($edge -eq 256) { 0 } else { $edge }))
        $writer.Write([byte]0)
        $writer.Write([byte]0)
        $writer.Write([uint16]1)
        $writer.Write([uint16]32)
        $writer.Write([uint32]$bytes.Length)
        $writer.Write([uint32]$offset)
        $offset += $bytes.Length
    }
    foreach ($bytes in $images) { $writer.Write($bytes) }
}
finally { $writer.Dispose() }
