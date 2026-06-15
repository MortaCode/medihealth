local key = KEYS[1]
local stock = redis.call('get', key)

if stock and tonumber(stock) > 0 then
    local newStock = redis.call('decr', key)
    return newStock
else
    return -1
end