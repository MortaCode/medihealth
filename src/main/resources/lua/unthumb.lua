-- Unlike: atomically decrement temp count and remove user mark
local tempKey = KEYS[1]
local userKey = KEYS[2]
local userId = ARGV[1]
local articleId = ARGV[2]

-- Check if liked
local liked = redis.call('hexists', userKey, articleId)
if liked == 0 then
    return 3  -- NOT_LIKED
end

-- Decrement temp count
local count = redis.call('hincrby', tempKey, articleId, -1)
if count <= 0 then
    redis.call('hdel', tempKey, articleId)
end
-- Remove user mark
redis.call('hdel', userKey, articleId)

return 1  -- SUCCESS
