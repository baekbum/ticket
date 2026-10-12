param(
    [Parameter(Mandatory)][string]$Jar,
    [string]$OutputDirectory = "$PSScriptRoot/results/idempotency/validation"
)
$ErrorActionPreference = 'Stop'
$Jar = (Resolve-Path $Jar).Path
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
$OutputDirectory = (Resolve-Path $OutputDirectory).Path
$redisName = 'ticket-queue-validation-redis'
$redisPort = 16401
$apps = [Collections.Generic.List[Diagnostics.Process]]::new()
if (docker ps -a --filter "name=^/$redisName$" --format '{{.Names}}') { throw '검증용 Redis가 이미 존재합니다.' }
foreach ($port in @(18084,18085)) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) { throw "포트 사용 중: $port" }
}
function Jwt([string]$User) {
    function Url64([byte[]]$Bytes) { [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+','-').Replace('/','_') }
    $header = Url64 ([Text.Encoding]::UTF8.GetBytes('{"alg":"HS256","typ":"JWT"}'))
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $payload = @{ sub=$User; auth='ROLE_USER'; tokenType='access'; tokenVersion=1; iat=$now; exp=($now + 7200) } | ConvertTo-Json -Compress
    $body = Url64 ([Text.Encoding]::UTF8.GetBytes($payload))
    $data = "$header.$body"
    $signer = [Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes('queue-baseline-only-secret-at-least-32-bytes-long'))
    try { return "$data.$(Url64 ($signer.ComputeHash([Text.Encoding]::UTF8.GetBytes($data))))" }
    finally { $signer.Dispose() }
}
function Request([string]$Method, [string]$Path, [hashtable]$Headers, [string]$Body) {
    $response = Invoke-WebRequest "http://127.0.0.1:18084$Path" -Method $Method -Headers $Headers -Body $Body -ContentType 'application/json' -SkipHttpErrorCheck -TimeoutSec 10
    $data = if ([string]::IsNullOrWhiteSpace($response.Content)) { $null } else { $response.Content | ConvertFrom-Json }
    return @{ status=[int]$response.StatusCode; data=$data }
}
& docker run --rm -d --name $redisName -p "127.0.0.1:${redisPort}:6379" redis:7.4-alpine | Set-Content "$OutputDirectory/redis-container.txt"
if ($LASTEXITCODE -ne 0) { throw 'Redis 시작 실패' }
try {
    foreach ($port in @(18084,18085)) {
        $arguments = @('-Xms256m','-Xmx512m','-jar',('"' + $Jar + '"'),"--server.port=$port",'--spring.profiles.active=baseline',
            '--spring.data.redis.host=127.0.0.1',"--spring.data.redis.port=$redisPort",'--spring.data.redis.database=2',
            '--token-state.redis.host=127.0.0.1',"--token-state.redis.port=$redisPort",'--token-state.redis.database=0',
            '--token.secret=queue-baseline-only-secret-at-least-32-bytes-long','--app.queue.admission-size=2',
            '--app.queue.max-sessions-per-user=2','--app.queue.cleanup-mode=reactive','--management.tracing.enabled=false')
        $app = Start-Process -FilePath (Get-Command java).Source -ArgumentList $arguments -PassThru -WindowStyle Hidden -RedirectStandardOutput "$OutputDirectory/app-$port.log" -RedirectStandardError "$OutputDirectory/app-$port-error.log"
        $apps.Add($app)
        $ready = $false
        for ($attempt=0; $attempt -lt 90; $attempt++) {
            if ($app.HasExited) { throw "앱 시작 실패: $port" }
            try { if ((Invoke-RestMethod "http://127.0.0.1:$port/actuator/health" -TimeoutSec 2).status -eq 'UP') { $ready=$true; break } } catch { }
            Start-Sleep -Seconds 1
        }
        if (!$ready) { throw "앱 준비 실패: $port" }
    }
    $commands = @('SET AUTH:STATE:cluster-shared 1:1:ROLE_USER EX 7200','SET AUTH:STATE:cluster-fault 1:1:ROLE_USER EX 7200','SET AUTH:STATE:cluster-replay 1:1:ROLE_USER EX 7200')
    $commands += 1..60 | ForEach-Object { "SET AUTH:STATE:cluster-user-$_ 1:1:ROLE_USER EX 7200" }
    $commands | docker exec -i $redisName redis-cli -n 0 --raw | Out-Null
    & docker run --rm --add-host=host.docker.internal:host-gateway -v "${PSScriptRoot}:/scripts:ro" -v "${OutputDirectory}:/results" grafana/k6:0.57.0 run --quiet /scripts/multi-instance-load.js 2>&1 | Out-File "$OutputDirectory/k6.log"
    if ($LASTEXITCODE -ne 0) { throw '다중 인스턴스 HTTP 검증 실패' }
    $summary = Get-Content "$OutputDirectory/summary.json" -Raw | ConvertFrom-Json
    $counts = @{ registered=$summary.metrics.shared_registered.values.count; limited=$summary.metrics.shared_limited.values.count;
        ready=$summary.metrics.distinct_ready.values.count; waiting=$summary.metrics.distinct_waiting.values.count }
    if ($counts.registered -ne 2 -or $counts.limited -ne 22 -or $counts.ready -ne 2 -or $counts.waiting -ne 10) {
        throw "동시성 불변식 실패: $($counts | ConvertTo-Json -Compress)"
    }
    $counts.replays = $summary.metrics.replay_ready.values.count
    $counts.replaySlots = [int](docker exec $redisName redis-cli -n 2 ZCARD queue:event:930004:active)
    if ($counts.replays -ne 24 -or $counts.replaySlots -ne 1) {
        throw '두 인스턴스에서 같은 활성 토큰을 복구하지 못했습니다.'
    }
    $counts.sharedSessions = [int](docker exec $redisName redis-cli -n 2 ZCARD queue:user-sessions:cluster-shared)
    $counts.activeSlots = [int](docker exec $redisName redis-cli -n 2 ZCARD queue:event:930002:active)
    $counts.waitingTokens = [int](docker exec $redisName redis-cli -n 2 ZCARD queue:event:930002:waiting)
    if ($counts.sharedSessions -ne 2 -or $counts.activeSlots -ne 2 -or $counts.waitingTokens -ne 10) {
        throw '두 인스턴스 요청 후 Redis 인덱스 정합성 실패'
    }
    $headers = @{ Authorization="Bearer $(Jwt 'cluster-fault')" }
    & docker pause $redisName | Out-Null
    try {
        $timer = [Diagnostics.Stopwatch]::StartNew()
        $authFailure = Request 'POST' '/api/v1/queue/events/930003/enter' $headers $null
        $timer.Stop()
        $authSeconds=$timer.Elapsed.TotalSeconds
        $timer.Restart()
        $queueFailure = Request 'POST' '/api/v1/queue/validate' @{} '{"eventId":930003,"userId":"cluster-fault","token":"missing"}'
        $timer.Stop()
        $queueSeconds=$timer.Elapsed.TotalSeconds
        if ($authFailure.status -ne 503 -or $queueFailure.status -ne 503) { throw 'Redis 장애의 503 응답 검증 실패' }
    } finally { & docker unpause $redisName | Out-Null }
    Start-Sleep -Seconds 1
    $entered = Request 'POST' '/api/v1/queue/events/930003/enter' $headers $null
    if ($entered.status -ne 200 -or $entered.data.status -ne 'WAITING') { throw 'Redis 복구 후 등록 실패' }
    $headers['X-Waiting-Token']=$entered.data.token
    # 첫 승격 응답을 받지 못한 클라이언트의 같은 대기 토큰 재시도를 재현한다.
    $promoted = Request 'GET' '/api/v1/queue/events/930003/status' $headers $null
    $retry = Request 'GET' '/api/v1/queue/events/930003/status' $headers $null
    if ($promoted.data.status -ne 'READY' -or $retry.status -ne 200 -or $retry.data.token -ne $promoted.data.token -or
            $retry.data.activeTokenExpiresAt -ne $promoted.data.activeTokenExpiresAt) {
        throw '응답 유실 후 같은 활성 토큰 복구 실패'
    }
    $activeCount = docker exec $redisName redis-cli -n 2 ZCARD queue:event:930003:active
    $activeTtl = docker exec $redisName redis-cli -n 2 TTL "queue:active-token:$($promoted.data.token)"
    if ([int]$activeCount -ne 1 -or [int]$activeTtl -le 0) { throw '응답 유실 후 세션 정합성 실패' }
    $completeBody = @{eventId=930003;userId='cluster-fault';token=$promoted.data.token} | ConvertTo-Json -Compress
    $completed = Request 'POST' '/api/v1/queue/complete' @{} $completeBody
    $completedRetry = Request 'GET' '/api/v1/queue/events/930003/status' $headers $null
    $mappingCount = [int](docker exec $redisName redis-cli -n 2 EXISTS "queue:waiting-active:$($entered.data.token)" "queue:active-waiting:$($promoted.data.token)")
    if ($completed.status -ne 204 -or $completedRetry.status -ne 400 -or $mappingCount -ne 0) {
        throw '완료 후 멱등 매핑 폐기 검증 실패'
    }
    @{ completedRetryStatus=$completedRetry.status;mappingKeyCountAfterCompletion=$mappingCount; processes=@($apps | ForEach-Object Id); concurrency=$counts; authenticationFailure=$authFailure;
        authenticationFailureSeconds=$authSeconds; queueFailure=$queueFailure; queueFailureSeconds=$queueSeconds;
        recoveredStatus=$entered.status; lostResponseRetryStatus=$retry.status; activeCount=[int]$activeCount; activeTtl=[int]$activeTtl } |
        ConvertTo-Json -Depth 6 | Set-Content "$OutputDirectory/validation.json"
    Write-Output '두 인스턴스의 세션 제한·입장 한도, Redis 장애·복구, 응답 유실 계약 검증 완료'
} finally {
    & docker unpause $redisName 2>$null | Out-Null
    foreach ($app in $apps) { if (!$app.HasExited) { Stop-Process -Id $app.Id } }
    & docker stop $redisName | Out-Null
}