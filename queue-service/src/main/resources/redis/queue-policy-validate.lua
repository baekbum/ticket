local capacity, reason = eventPolicy(KEYS[1], ARGV[1] == '1')
return reason or 'OK'
