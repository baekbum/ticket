local actual = redis.call('TYPE', KEYS[1]).ok
if actual ~= 'none' and actual ~= 'hash' then return redis.error_reply('QUEUE_WRONG_POLICY_TYPE') end
local incoming = ARGV[1]
if not string.match(incoming, '^%d+$') or incoming == '0' then return redis.error_reply('QUEUE_INVALID_VERSION') end
local previous = redis.call('HGET', KEYS[1], 'version')
-- BIGINT를 Lua double로 변환하지 않아도 버전 순서를 정확하게 비교한다.
if previous and (#previous > #incoming or (#previous == #incoming and previous >= incoming)) then return 0 end
redis.call('HSET', KEYS[1], 'version', incoming, 'status', ARGV[2], 'saleStartAt', ARGV[3],
    'saleEndAt', ARGV[4], 'eventDateTime', ARGV[5], 'admissionSize', ARGV[6])
return 1
