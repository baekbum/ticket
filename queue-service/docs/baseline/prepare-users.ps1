param(
    [ValidateRange(1, 10000)][int]$Users = 100,
    [ValidatePattern('^[a-zA-Z0-9-]+$')][string]$RunId = "mvc-baseline"
)
$ErrorActionPreference = 'Stop'
# 기준 측정 절차에서 생성한 일회용 테스트 Redis만 사용한다.
$commands = for ($index = 1; $index -le $Users; $index++) {
    "SET AUTH:STATE:${RunId}-${index} 1:1:ROLE_USER EX 7200"
}
$commands | docker exec -i ticket-queue-baseline-redis redis-cli -n 0 --raw | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not prepare authentication states' }
Write-Output "Prepared $Users test users; RUN_ID=$RunId"
