-- 스크립트가 사용하는 모든 키는 KEYS로 전달한다. 쓰기 전에 타입을 검증한다.
local expected = {'zset', 'zset', 'string', 'zset', 'string', 'zset', 'string', 'string', 'string', 'string'}
for index, key in ipairs(KEYS) do
    local actual = redis.call('TYPE', key).ok
    if actual ~= 'none' and actual ~= expected[index] then
        return redis.error_reply('QUEUE_WRONG_KEY_TYPE')
    end
end

local waitingKey = KEYS[1]
local activeKey = KEYS[2]
local newActiveTokenKey = KEYS[3]
local userSessionsKey = KEYS[4]
local waitingTokenKey = KEYS[5]
local waitingExpiryKey = KEYS[6]
local waitingActiveMappingKey = KEYS[7]
local newActiveWaitingMappingKey = KEYS[8]
local existingActiveTokenKey = KEYS[9]
local existingActiveWaitingMappingKey = KEYS[10]

local waitingToken = ARGV[1]
local newActiveToken = ARGV[2]
local tokenValue = ARGV[3]
local activeTtlMillis = tonumber(ARGV[4])
local admissionSize = tonumber(ARGV[5])
local waitingTtlMillis = tonumber(ARGV[6])
local newActiveSessionMember = ARGV[7]
local waitingSessionMember = ARGV[8]
local existingActiveToken = ARGV[9]

-- 같은 만료 시각을 토큰·매핑·인덱스에 적용하도록 Redis 서버 시각을 사용한다.
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
local mappedToken = redis.call('GET', waitingActiveMappingKey)
if mappedToken then
    -- 사전 조회 이후 다른 요청이 승격한 경우, 해당 토큰의 키를 다시 전달받는다.
    -- 재조회 전에는 어떠한 쓰기도 하지 않는다.
    if mappedToken ~= existingActiveToken then
        return 'RETRY'
    end
    local expiresAt = redis.call('ZSCORE', activeKey, mappedToken)
    if redis.call('GET', existingActiveTokenKey) == tokenValue
            and redis.call('GET', existingActiveWaitingMappingKey) == waitingToken
            and redis.call('PTTL', existingActiveTokenKey) > 0
            and expiresAt and tonumber(expiresAt) > now then
        -- 다른 사용자·이벤트의 토큰은 반환하지 않으며 TTL도 연장하지 않는다.
        return 'READY:' .. mappedToken
    end
    return 'INVALID'
end

if redis.call('GET', waitingTokenKey) ~= tokenValue then
    return 'INVALID'
end
local rank = redis.call('ZRANK', waitingKey, waitingToken)
if not rank then
    return 'INVALID'
end

local availableSlots = admissionSize - redis.call('ZCARD', activeKey)
if availableSlots <= 0 or rank >= availableSlots then
    -- 승격 판단과 대기 갱신 사이에도 다른 승격이 끼어들지 않게 한다.
    local expiresAt = now + waitingTtlMillis
    if not redis.call('SET', waitingTokenKey, tokenValue, 'XX', 'PXAT', expiresAt) then
        return 'INVALID'
    end
    redis.call('ZADD', waitingExpiryKey, expiresAt, waitingToken)
    redis.call('ZADD', userSessionsKey, expiresAt, waitingSessionMember)
    return 'WAITING'
end

local expiresAt = now + activeTtlMillis
redis.call('SET', newActiveTokenKey, tokenValue, 'PXAT', expiresAt)
redis.call('SET', waitingActiveMappingKey, newActiveToken, 'PXAT', expiresAt)
redis.call('SET', newActiveWaitingMappingKey, waitingToken, 'PXAT', expiresAt)
redis.call('ZADD', activeKey, expiresAt, newActiveToken)
redis.call('ZADD', userSessionsKey, expiresAt, newActiveSessionMember)
redis.call('ZREM', waitingKey, waitingToken)
redis.call('ZREM', userSessionsKey, waitingSessionMember)
redis.call('ZREM', waitingExpiryKey, waitingToken)
redis.call('DEL', waitingTokenKey)
return 'READY:' .. newActiveToken