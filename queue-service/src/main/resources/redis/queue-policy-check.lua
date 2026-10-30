-- 요청 시 Ticket 호출 없이 Redis 서버 시각과 영속 정책 사본으로 판단한다.
local function eventPolicy(key, existing)
    local kind = redis.call('TYPE', key).ok
    if kind ~= 'hash' then return nil, 'POLICY_UNAVAILABLE' end
    local p = redis.call('HMGET', key, 'status', 'saleStartAt', 'saleEndAt', 'eventDateTime', 'admissionSize', 'version')
    local start = tonumber(p[2])
    local finish = tonumber(p[3])
    local performance = tonumber(p[4])
    local capacity = tonumber(p[5])
    if not start or not finish or not performance or not capacity or not p[6]
        or start < 0 or finish < start or performance <= 0 or capacity < 1 or capacity % 1 ~= 0 then
        return nil, 'POLICY_UNAVAILABLE'
    end
    local t = redis.call('TIME')
    local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
    if now < start then return nil, 'SALE_NOT_STARTED' end
    if now > finish or now >= performance then return nil, 'SALE_ENDED' end
    if p[1] ~= 'ON_SALE' and not (existing and p[1] == 'SOLD_OUT') then
        return nil, 'EVENT_NOT_BOOKABLE'
    end
    return capacity, nil
end
