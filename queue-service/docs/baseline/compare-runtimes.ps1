param(
    [Parameter(Mandatory)][string]$MvcJar,
    [Parameter(Mandatory)][string]$WebFluxJar,
    [int]$Repeats = 3,
    [int]$Vus = 100,
    [string]$Duration = '60s',
    [string]$OutputDirectory = "$PSScriptRoot/results/step5/comparison-complete"
)
$ErrorActionPreference = 'Stop'
$java = (Get-Command java).Source
$redisName = 'ticket-queue-comparison-redis'
$redisPort = 16400
$port = 18083
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
$OutputDirectory = (Resolve-Path $OutputDirectory).Path
$MvcJar = (Resolve-Path $MvcJar).Path
$WebFluxJar = (Resolve-Path $WebFluxJar).Path
if (docker ps -a --filter "name=^/$redisName$" --format '{{.Names}}') {
    throw "비교용 컨테이너가 이미 존재합니다: $redisName"
}
if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
    throw "앱 포트가 이미 사용 중입니다: $port"
}
function Run-Load([string]$RunId, [long]$EventId, [int]$Users, [string]$Length, [string]$Directory) {
    New-Item -ItemType Directory -Force $Directory | Out-Null
    & "$PSScriptRoot/prepare-users.ps1" -Users $Users -RunId $RunId -RedisContainer $redisName
    & docker run --rm --add-host=host.docker.internal:host-gateway -v "${PSScriptRoot}:/scripts:ro" -v "${Directory}:/results" -e "RUN_ID=$RunId" -e "EVENT_ID=$EventId" -e "VUS=$Users" -e "DURATION=$Length" grafana/k6:0.57.0 run --quiet /scripts/queue-load.js 2>&1 | Out-File "$Directory/k6.log"
    $exitCode = $LASTEXITCODE
    $exitCode | Set-Content "$Directory/k6-exit-code.txt"
    # 임계치 실패(99)도 비교 결과로 보존한다. 앱·스크립트 실행 자체의 실패는 중단한다.
    if ($exitCode -ne 0 -and $exitCode -ne 99) { throw "k6 실행 실패: $RunId, exit=$exitCode" }
    if ($exitCode -eq 99) { Write-Output "k6 성공 기준 미충족: $RunId (결과 보존)" }
}
for ($round = 1; $round -le $Repeats; $round++) {
    # 순서에 따른 호스트 상태 차이를 줄이기 위해 번갈아 실행한다.
    $order = if ($round % 2 -eq 1) { @('mvc', 'webflux') } else { @('webflux', 'mvc') }
    foreach ($runtime in $order) {
        $run = "$runtime-$round"
        $directory = Join-Path $OutputDirectory $run
        New-Item -ItemType Directory -Force $directory | Out-Null
        $app = $null
        $sampler = $null
        & docker run --rm -d --name $redisName -p "127.0.0.1:${redisPort}:6379" redis:7.4-alpine | Out-File "$directory/redis-container.txt"
        if ($LASTEXITCODE -ne 0) { throw '비교용 Redis 시작 실패' }
        try {
            $jar = if ($runtime -eq 'mvc') { $MvcJar } else { $WebFluxJar }
            $arguments = @(
                '-Xms256m', '-Xmx512m', '-jar', ('"' + $jar + '"'),
                "--server.port=$port", '--spring.profiles.active=baseline',
                '--spring.data.redis.host=127.0.0.1', "--spring.data.redis.port=$redisPort", '--spring.data.redis.database=2',
                '--token-state.redis.host=127.0.0.1', "--token-state.redis.port=$redisPort", '--token-state.redis.database=0',
                '--token.secret=queue-baseline-only-secret-at-least-32-bytes-long',
                '--app.queue.admission-size=5', '--app.queue.cleanup-fixed-delay-ms=20000', '--management.tracing.enabled=false'
            )
            $app = Start-Process -FilePath $java -ArgumentList $arguments -PassThru -WindowStyle Hidden -RedirectStandardOutput "$directory/app.log" -RedirectStandardError "$directory/app-error.log"
            @{ runtime=$runtime; round=$round; jar=$jar; processId=$app.Id; args=$arguments; vus=$Vus; duration=$Duration } | ConvertTo-Json -Depth 5 | Set-Content "$directory/manifest.json" -Encoding UTF8
            $ready = $false
            for ($attempt = 0; $attempt -lt 90; $attempt++) {
                if ($app.HasExited) { throw "앱 시작 실패: $run" }
                try {
                    $health = Invoke-RestMethod "http://127.0.0.1:$port/actuator/health" -TimeoutSec 2
                    if ($health.status -eq 'UP') { $ready = $true; break }
                } catch { }
                Start-Sleep -Seconds 1
            }
            if (!$ready) { throw "앱 준비 시간 초과: $run" }
            Run-Load "$run-warm" (910000 + $round) 20 '20s' "$directory/warmup"
            & docker exec $redisName redis-cli INFO stats | Set-Content "$directory/redis-stats-before.txt"
            & docker exec $redisName redis-cli INFO commandstats | Set-Content "$directory/redis-commands-before.txt"
            $samplerArgs = @('-NoProfile', '-File', ('"' + "$PSScriptRoot/sample-resources.ps1" + '"'), '-Seconds', '75', '-OutputDirectory', ('"' + $directory + '"'))
            $sampler = Start-Process -FilePath (Get-Command pwsh).Source -ArgumentList $samplerArgs -PassThru -WindowStyle Hidden -RedirectStandardOutput "$directory/sampler.log" -RedirectStandardError "$directory/sampler-error.log"
            $before = Get-Process -Id $app.Id
            $cpuBefore = $before.CPU
            $timer = [Diagnostics.Stopwatch]::StartNew()
            Run-Load $run (920000 + $round) $Vus $Duration $directory
            $timer.Stop()
            $after = Get-Process -Id $app.Id
            @{ elapsedSeconds=$timer.Elapsed.TotalSeconds; processCpuSeconds=($after.CPU - $cpuBefore); workingSetMiB=($after.WorkingSet64 / 1MB) } | ConvertTo-Json | Set-Content "$directory/cpu.json"
            & docker exec $redisName redis-cli INFO stats | Set-Content "$directory/redis-stats-after.txt"
            & docker exec $redisName redis-cli INFO commandstats | Set-Content "$directory/redis-commands-after.txt"
            $sampler.WaitForExit()
            if ($sampler.ExitCode -ne 0) { throw "자원 수집 실패: $run" }
            $result = Get-Content "$directory/summary.json" -Raw | ConvertFrom-Json
            Write-Output "$run 완료: HTTP $($result.summary.metrics.http_reqs.values.count), status p95=$($result.summary.metrics.queue_status_duration.values.'p(95)')ms"
        } finally {
            # 이 실행에서 생성한 프로세스와 컨테이너만 종료한다.
            if ($sampler -and !$sampler.HasExited) { Stop-Process -Id $sampler.Id }
            if ($app -and !$app.HasExited) { Stop-Process -Id $app.Id }
            & docker stop $redisName | Out-Null
            # --rm 컨테이너는 stop 반환 직후에도 비동기 제거 중일 수 있다.
            for ($attempt = 0; $attempt -lt 30; $attempt++) {
                if (!(docker ps -a --filter "name=^/$redisName$" --format '{{.Names}}')) { break }
                Start-Sleep -Seconds 1
            }
            if (docker ps -a --filter "name=^/$redisName$" --format '{{.Names}}') {
                throw '이전 비교 Redis 컨테이너 제거 시간 초과'
            }
        }
    }
}