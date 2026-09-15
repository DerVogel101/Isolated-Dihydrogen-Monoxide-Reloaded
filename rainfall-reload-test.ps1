$ErrorActionPreference = 'Stop'

$root = $PSScriptRoot
$wrapper = Join-Path $root 'gradlew.bat'

function Invoke-RainfallPhase([string] $profile) {
    $arguments = @('/d', '/c', 'call', "`"$wrapper`"", "-P$profile", 'runServer', '--args=--nogui', '--no-daemon', '--console=plain')
    $process = Start-Process -FilePath 'cmd.exe' -ArgumentList $arguments -WorkingDirectory $root -NoNewWindow -PassThru
    if (-not $process.WaitForExit(180000)) {
        $process.Kill($true)
        throw "Rainfall reload phase timed out: $profile"
    }
    if ($process.ExitCode -ne 0) {
        throw "Rainfall reload phase failed: $profile"
    }
}

Invoke-RainfallPhase 'rainfallReloadSeed'
Invoke-RainfallPhase 'rainfallTest'
