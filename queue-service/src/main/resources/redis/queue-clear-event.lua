-- 활성 목록 조회 이후 승격·완료가 있었다면 다시 조회한다. 아직 키를 삭제하지 않는다.
if redis.call('ZCARD', KEYS[2]) ~= #ARGV then
    return -1
end
for _, token in ipairs(ARGV) do
    if not redis.call('ZSCORE', KEYS[2], token) then
        return -1
    end
end
-- 목록 확인과 삭제를 한 번에 수행해 새로 승격된 토큰의 매핑을 놓치지 않는다.
return redis.call('DEL', unpack(KEYS))