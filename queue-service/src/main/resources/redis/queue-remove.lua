-- 잘못된 키 타입은 쓰기 전에 거부한다. Lua 실행 오류 자체에는 롤백이 없다.
local expected = {'zset', 'zset', 'string', 'zset'}
for index, key in ipairs(KEYS) do
    local actual = redis.call('TYPE', key).ok
    if actual ~= 'none' and actual ~= expected[index] then
        return redis.error_reply('QUEUE_WRONG_KEY_TYPE')
    end
end
-- 조회 이후 갱신된 토큰은 만료 정리에서 제외한다. 음수 기준은 명시적 삭제다.
local cutoff = tonumber(ARGV[3])
if cutoff >= 0 then
    local expiresAt = redis.call('ZSCORE', KEYS[2], ARGV[1])
    if not expiresAt or tonumber(expiresAt) > cutoff then
        return 0
    end
end
redis.call('DEL', KEYS[3])
if ARGV[4] == '1' then
    redis.call('ZREM', KEYS[4], ARGV[2])
end
redis.call('ZREM', KEYS[1], ARGV[1])
redis.call('ZREM', KEYS[2], ARGV[1])
return 1