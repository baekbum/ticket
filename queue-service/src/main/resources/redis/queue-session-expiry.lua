local function queueTimeMillis()
    local time = redis.call('TIME')
    return tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
end

-- 요청 시각이 아니라 남아 있는 세션 중 가장 늦은 만료 시각까지 인덱스를 유지한다.
local function updateUserSessionsExpiry(key)
    local latest = redis.call('ZREVRANGE', key, 0, 0, 'WITHSCORES')
    if #latest > 0 then
        redis.call('PEXPIREAT', key, latest[2])
    end
end
