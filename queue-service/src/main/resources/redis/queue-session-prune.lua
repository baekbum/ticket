-- Java에서 조회한 멤버와 토큰 키를 전달받되, 유효성 확인과 삭제는 함께 실행한다.
for index, key in ipairs(KEYS) do
    local expected = index == 1 and 'zset' or 'string'
    local actual = redis.call('TYPE', key).ok
    if actual ~= 'none' and actual ~= expected then
        return redis.error_reply('QUEUE_WRONG_KEY_TYPE')
    end
end

local removed = 0
for index, member in ipairs(ARGV) do
    -- 멤버 목록 조회 이후 명시적으로 삭제한 세션은 다시 만들지 않는다.
    if redis.call('ZSCORE', KEYS[1], member) then
        local value = redis.call('GET', KEYS[index + 1])
        local expiresAt = redis.call('PEXPIRETIME', KEYS[index + 1])
        if not value or not string.find(value, '%S') or expiresAt <= 0 then
            removed = removed + redis.call('ZREM', KEYS[1], member)
        else
            -- TTL 없는 기존 인덱스도 실제 토큰의 만료 시각에 맞춰 보정한다.
            redis.call('ZADD', KEYS[1], expiresAt, member)
        end
    end
end
updateUserSessionsExpiry(KEYS[1])
return removed
