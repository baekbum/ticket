-- 키 타입을 먼저 확인해 잘못된 데이터로 인한 부분 쓰기를 방지한다.
local expected = {'zset', 'zset', 'string', 'zset', 'string', 'zset'}
for index, key in ipairs(KEYS) do
    local actual = redis.call('TYPE', key).ok
    if actual ~= 'none' and actual ~= expected[index] then
        return redis.error_reply('QUEUE_WRONG_KEY_TYPE')
    end
end
local waitingKey = KEYS[1]
local activeKey = KEYS[2]
local activeTokenKey = KEYS[3]
local userSessionsKey = KEYS[4]
local waitingTokenKey = KEYS[5]
local waitingExpiryKey = KEYS[6]
local waitingToken = ARGV[1]
local activeToken = ARGV[2]
local tokenValue = ARGV[3]
local expiresAt = tonumber(ARGV[4])
local activeTokenTtlMillis = tonumber(ARGV[5])
local admissionSize = tonumber(ARGV[6])
local activeSessionMember = ARGV[7]
local waitingSessionMember = ARGV[8]

if redis.call('GET', waitingTokenKey) ~= tokenValue then
    return 0
end

local rank = redis.call('ZRANK', waitingKey, waitingToken)
if not rank then
    return 0
end

local activeCount = redis.call('ZCARD', activeKey)
local availableSlots = admissionSize - activeCount
if availableSlots <= 0 or rank >= availableSlots then
    return 0
end

redis.call('SET', activeTokenKey, tokenValue, 'PX', activeTokenTtlMillis)
redis.call('ZADD', activeKey, expiresAt, activeToken)
redis.call('ZADD', userSessionsKey, expiresAt, activeSessionMember)
redis.call('ZREM', waitingKey, waitingToken)
redis.call('ZREM', userSessionsKey, waitingSessionMember)
redis.call('ZREM', waitingExpiryKey, waitingToken)
redis.call('DEL', waitingTokenKey)
return 1
