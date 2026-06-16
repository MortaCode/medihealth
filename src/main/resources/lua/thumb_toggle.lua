-- Atomic like/unlike toggle
-- KEYS[1]: tempKey  (like:temp:{timeslice})
-- KEYS[2]: userKey  (like:{userId})
-- ARGV[1]: userId
-- ARGV[2]: articleId
-- Returns: 1=liked, 2=unliked

local tempKey = KEYS[1]
local userKey = KEYS[2]
local userId = ARGV[1]
local articleId = ARGV[2]
local hashKey = userId .. ':' .. articleId

local liked = redis.call('hexists', userKey, articleId)

if liked == 1 then
    -- Currently liked → unlike: record -1 in temp hash
    redis.call('hset', tempKey, hashKey, -1)
    redis.call('hdel', userKey, articleId)
    return 2
else
    -- Currently not liked → like: record +1 in temp hash
    redis.call('hset', tempKey, hashKey, 1)
    redis.call('hset', userKey, articleId, '1')
    return 1
end
