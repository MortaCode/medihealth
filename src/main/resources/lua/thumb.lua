-- Like: atomically increment temp count and mark user liked
local tempKey = KEYS[1]
local userKey = KEYS[2]
local userId = ARGV[1]
local articleId = ARGV[2]

-- Check already liked
local liked = redis.call('hexists', userKey, articleId)
if liked == 1 then
    return 2  -- ALREADY_LIKED
end

-- Increment temp count
redis.call('hincrby', tempKey, articleId, 1)
-- Mark user liked
redis.call('hset', userKey, articleId, '1')

return 1  -- SUCCESS
