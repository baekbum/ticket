-- 잘못된 키 타입은 쓰기 전에 거부한다. Lua 실행 오류 자체에는 롤백이 없다.
local expected = {'zset', 'zset', 'string', 'zset'}
for index, key in ipairs(KEYS) do
    local actual = redis.call('TYPE', key).ok
    if actual ~= 'none' and actual ~= expected[index] then
        return redis.error_reply('QUEUE_WRONG_KEY_TYPE')
    end
end
-- 승격·탈퇴·만료로 제거된 대기 토큰은 갱신으로 다시 만들지 않는다.
if redis.call('GET', KEYS[3]) ~= ARGV[2] or not redis.call('ZSCORE', KEYS[1], ARGV[1]) then
    return 0
end
redis.call('SET', KEYS[3], ARGV[2], 'XX', 'PX', ARGV[3])
redis.call('ZADD', KEYS[2], ARGV[4], ARGV[1])
redis.call('ZADD', KEYS[4], ARGV[4], ARGV[5])
return 1