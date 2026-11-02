local removed = redis.call('ZREM', KEYS[1], ARGV[1])
updateUserSessionsExpiry(KEYS[1])
return removed
