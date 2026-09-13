param([Parameter(Mandatory = $true)][string]$MinecraftJar)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Drawing
$destination = Join-Path $PSScriptRoot '../src/main/resources/assets/immersivefluids/textures/block'
New-Item -ItemType Directory -Force $destination | Out-Null
$archive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $MinecraftJar))
try {
    $entry = $archive.GetEntry('assets/minecraft/textures/block/iron_block.png')
    if ($null -eq $entry) { throw 'Minecraft jar does not contain the iron block texture.' }
    $basePath = Join-Path $destination 'machinery_iron.png'
    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $basePath, $true)
} finally { $archive.Dispose() }
$base = [Drawing.Bitmap]::new($basePath)
$specular = [Drawing.Bitmap]::new($base.Width, $base.Height)
$normal = [Drawing.Bitmap]::new($base.Width, $base.Height)
try {
    for ($y = 0; $y -lt $base.Height; $y++) {
        for ($x = 0; $x -lt $base.Width; $x++) {
            # LabPBR: perceptual smoothness, iron conductor ID, no porosity, no emission (255).
            $smoothness = 110 + [int](80 * $base.GetPixel($x, $y).R / 255)
            $specular.SetPixel($x, $y, [Drawing.Color]::FromArgb(255, $smoothness, 230, 0))
            # LabPBR: flat tangent-space XY normal, full ambient visibility, full height.
            $normal.SetPixel($x, $y, [Drawing.Color]::FromArgb(255, 128, 128, 255))
        }
    }
    $specular.Save((Join-Path $destination 'machinery_iron_s.png'), [Drawing.Imaging.ImageFormat]::Png)
    $normal.Save((Join-Path $destination 'machinery_iron_n.png'), [Drawing.Imaging.ImageFormat]::Png)
} finally { $base.Dispose(); $specular.Dispose(); $normal.Dispose() }
