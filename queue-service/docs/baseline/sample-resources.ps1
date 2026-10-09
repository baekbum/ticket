param(
    [string]$BaseUrl = 'http://127.0.0.1:18083',
    [int]$Seconds = 75,
    [string]$OutputDirectory = "$PSScriptRoot/results/mvc-reference"
)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
$rows = [Collections.Generic.List[object]]::new()
$timer = [Diagnostics.Stopwatch]::StartNew()
while ($timer.Elapsed.TotalSeconds -lt $Seconds) {
    $response = Invoke-WebRequest -UseBasicParsing "$BaseUrl/actuator/prometheus"
    $content = $response.Content
    if ($content -is [byte[]]) { $content = [Text.Encoding]::UTF8.GetString($content) }
    $lines = $content -split "`n"
    $values = @{}
    foreach ($metric in @('process_cpu_usage', 'jvm_threads_live_threads', 'process_cpu_time_ns_total')) {
        $line = $lines | Where-Object { $_ -match "^$metric(?:\{| )" } | Select-Object -First 1
        if (!$line) { throw "Missing metric: $metric" }
        $values[$metric] = [double]::Parse(($line -split ' ')[-1], [Globalization.CultureInfo]::InvariantCulture)
    }
    $heap = 0.0
    foreach ($line in ($lines | Where-Object { $_ -match '^jvm_memory_used_bytes\{.*area="heap"' })) {
        $heap += [double]::Parse(($line -split ' ')[-1], [Globalization.CultureInfo]::InvariantCulture)
    }
    $rows.Add([pscustomobject]@{
        utc = [DateTime]::UtcNow.ToString('o')
        processCpuUsage = $values.process_cpu_usage
        processCpuSeconds = $values.process_cpu_time_ns_total / 1e9
        liveThreads = $values.jvm_threads_live_threads
        heapMiB = $heap / 1MB
    })
    Start-Sleep -Seconds 2
}
$rows | Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $OutputDirectory 'resources.csv')
