-- KEYS[1]: 库存 key (String)
-- KEYS[2]: 用户订单记录 key (Hash)
-- ARGV[1]: 用户唯一标识 (如 user_id)

local stock_key = KEYS[1]
local user_orders_key = KEYS[2]
local user_id = ARGV[1]

-- 1. 一人一单校验：检查该用户是否已下单
local user_bought = redis.call('hexists', user_orders_key, user_id)
if user_bought == 1 then
    return -2  -- -2 表示用户已购买过
end

-- 2. 库存检查与扣减
local stock = redis.call('get', stock_key)
if not stock or tonumber(stock) <= 0 then
    return -1  -- -1 表示库存不足或无库存
end

-- 扣减库存
local new_stock = redis.call('decr', stock_key)

-- 3. 记录该用户已下单 (使用 Hash 存储，方便后续查询)
redis.call('hset', user_orders_key, user_id, '1')

return new_stock  -- 返回剩余库存（≥0）