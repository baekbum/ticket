-- 잘못된 키 타입은 쓰기 전에 거부한다. Lua 실행 오류 자체에는 롤백이 없다.
local expected = {'zset', 'zset', 'string', 'zset'}
for index, key in ipairs(KEYS) do
    local actual = redis.call('TYPE', key).ok
    if actual ~= 'none' and actual ~= expected[index] then
        return redis.error_reply('QUEUE_WRONG_KEY_TYPE')
    end
end
-- 세션 한도 확인과 대기 토큰 등록을 한 번에 실행한다.
if redis.call('ZCARD', KEYS[4]) >= tonumber(ARGV[6]) then
    return 0
end
redis.call('SET', KEYS[3], ARGV[2], 'PX', ARGV[5])
redis.call('ZADD', KEYS[1], ARGV[3], ARGV[1])
redis.call('ZADD', KEYS[2], ARGV[4], ARGV[1])
redis.call('ZADD', KEYS[4], ARGV[4], ARGV[7])
return 1