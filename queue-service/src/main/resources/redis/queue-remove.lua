-- 잘못된 키 타입은 쓰기 전에 거부한다. Lua 실행 오류 자체에는 롤백이 없다.
-- 활성 토큰 삭제에는 역매핑과 정방향 매핑 키를 추가로 전달한다.
local expected = {'zset', 'zset', 'string', 'zset', 'string', 'string'}
for index, key in ipairs(KEYS) do
    local actual = redis.call('TYPE', key).ok
    if actual ~= 'none' and actual ~= expected[index] then
        return redis.error_reply('QUEUE_WRONG_KEY_TYPE')
    end
end

local token = ARGV[1]
local sessionMember = ARGV[2]
local cutoff = tonumber(ARGV[3])
local removeUserSession = ARGV[4] == '1'
local waitingToken = ARGV[5]
-- 조회 이후 갱신된 토큰은 만료 정리에서 제외한다. 음수 기준은 명시적 삭제다.
if cutoff >= 0 then
    local expiresAt = redis.call('ZSCORE', KEYS[2], token)
    if not expiresAt or tonumber(expiresAt) > cutoff then
        return 0
    end
end

local removed = 0
if #KEYS == 6 then
    -- 다른 활성 토큰을 가리키는 매핑은 삭제하지 않는다.
    if waitingToken ~= '' and redis.call('GET', KEYS[5]) == waitingToken
            and redis.call('GET', KEYS[6]) == token then
        removed = removed + redis.call('DEL', KEYS[6])
    end
    removed = removed + redis.call('DEL', KEYS[5])
end
removed = removed + redis.call('DEL', KEYS[3])
if removeUserSession then
    removed = removed + redis.call('ZREM', KEYS[4], sessionMember)
end
removed = removed + redis.call('ZREM', KEYS[1], token)
removed = removed + redis.call('ZREM', KEYS[2], token)
return removed