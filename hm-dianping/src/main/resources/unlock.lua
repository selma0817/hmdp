-- compare if current thread marker and marker in lock are the same
if(redis.call('get', KEYS[1]) == ARGV[1]) then
    -- if they are the same, the lock is owned by current thread, can delete
    return redis.call('del', KEYS[1])
end
return 0