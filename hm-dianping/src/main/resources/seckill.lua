-- 1. param list
-- 1.1 voucher id
local voucherId = ARGV[1]
-- 1.2 user id
local userId = ARGV[2]
-- 1.3 order id
local orderId = ARGV[3]

-- 2. data key
-- 2.1. stock key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2. order key
local orderKey = 'seckill:order:' .. voucherId

-- 3 scripting
-- 3.1 decide if enough stock
if(tonumber(redis.call('get', stockKey)) <= 0) then
    -- not enough stock
    return 1
end
-- 3.2 decide if user has ordered
if(redis.call('sismember', orderKey, userId)==1) then
    -- exist return 2
    return 2
end
-- 3.3 decrement stock incrby stockKey -1
redis.call('incrby', stockKey, -1)
-- 3.4 put to order key
redis.call('sadd', orderKey, userId)

--3.6 send message to message queue, XADD stream.orders * k1 v1 k2 v2
redis.call('xadd','stream.orders','*','userId',userId, 'voucherId',voucherId, 'id', orderId)

return 0