param([int]$FirstPair = 1, [int]$LastPair = 3,
      [ValidateSet('fixed', 'clear', 'allocation')][string]$Mode = 'fixed')
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if ($FirstPair -lt 1 -or $LastPair -lt $FirstPair) { throw 'Invalid pair range' }
foreach ($pair in $FirstPair..$LastPair) {
    # Alternate order to reduce systematic warm-machine ordering bias.
    $variants = if ($pair % 2 -eq 0) { @('baseline', 'candidate') } else { @('candidate', 'baseline') }
    foreach ($variant in $variants) {
        $run = "$variant-$Mode-$pair"
        & rtk proxy python tools/prepare-performance-run.py $run
        if ($LASTEXITCODE -ne 0) { throw "Could not prepare $run" }
        $arguments = @("-PfluidPerformanceTest=$run", 'runClient', '--no-daemon')
        if ($variant -eq 'baseline') { $arguments += '-PperformanceBaseline' }
        if ($Mode -eq 'clear') { $arguments += '-PperformanceClear' }
        if ($Mode -eq 'allocation') { $arguments += '-PperformanceAllocations' }
        & rtk proxy .\gradlew.bat @arguments *> "build/performance/runs/$run/launch.log"
        if ($LASTEXITCODE -ne 0) { throw "Client failed: $run" }
        $log = Get-Content "build/performance/runs/$run/logs/latest.log" -Raw
        if (!$log.Contains('FLUID_WORLD_PERFORMANCE_PASS')) { throw "Missing pass marker: $run" }
        Write-Output "Completed $run"
        Get-Content "build/performance/runs/$run/metrics.txt"
    }
}
