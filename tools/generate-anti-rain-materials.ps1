param(
    [string]$Source = (Join-Path (Split-Path -Parent $PSScriptRoot) 'block_bench'),
    [string]$Destination = (Join-Path (Split-Path -Parent $PSScriptRoot) 'src/main/resources/assets/immersivefluids/textures/block')
)

Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force -Path $Destination | Out-Null

foreach ($name in @('beacon', 'beacon_off')) {
    $sourceFile = Join-Path $Source ($name + '.png')
    if (!(Test-Path -LiteralPath $sourceFile)) { throw "Missing source texture: $sourceFile" }

    $color = [Drawing.Bitmap]::new($sourceFile)
    $normal = [Drawing.Bitmap]::new($color.Width, $color.Height)
    $specular = [Drawing.Bitmap]::new($color.Width, $color.Height)
    try {
        if ($color.Width -ne 16 -or $color.Height -ne 16) { throw "$sourceFile must be 16x16" }
        $emission = if ($name -eq 'beacon') { 254 } else { 32 }
        for ($y = 0; $y -lt $color.Height; $y++) {
            for ($x = 0; $x -lt $color.Width; $x++) {
                $normal.SetPixel($x, $y, [Drawing.Color]::FromArgb(255, 128, 128, 255))
                $specular.SetPixel($x, $y, [Drawing.Color]::FromArgb($emission, 180, 10, 0))
            }
        }
        Copy-Item -LiteralPath $sourceFile -Destination (Join-Path $Destination ($name + '.png')) -Force
        $normal.Save((Join-Path $Destination ($name + '_n.png')), [Drawing.Imaging.ImageFormat]::Png)
        $specular.Save((Join-Path $Destination ($name + '_s.png')), [Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $color.Dispose()
        $normal.Dispose()
        $specular.Dispose()
    }
}
