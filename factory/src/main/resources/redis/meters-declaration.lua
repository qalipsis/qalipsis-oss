-- Declares meter levels of a campaign and returns their short ids and owners.
-- The first declaring factory of a meter level becomes its owner. The short id of a meter level determines its shard:
-- short id modulo the count of shards.
--
-- KEYS[1]: counter generating the short ids of the meter levels
-- KEYS[2]: hash of the declared meter levels, by key of the meter level -> "<id>|<owner>"
-- KEYS[3]: set of the shards containing meter levels in the campaign
-- KEYS[4..]: none, all the keys share the hash tag of the campaign registry
--
-- ARGV[1]: identifier of the declaring factory
-- ARGV[2]: TTL of the keys, in seconds
-- ARGV[3]: count of shards
-- ARGV[4..]: keys of the meter levels to declare
--
-- Returns: { { "<id>|<owner>" for each declared meter level, in the order of ARGV }, { shards of the campaign } }
local shards = tonumber(ARGV[3])
local declarations = {}
for i = 4, #ARGV do
    -- A meter level already declared by any factory keeps its short id and owner.
    local declaration = redis.call('HGET', KEYS[2], ARGV[i])
    if not declaration then
        -- First declaration: new short id, owned by the declaring factory, and its shard is marked as used, so that
        -- all the factories acknowledge the periods to it.
        local id = redis.call('INCR', KEYS[1])
        declaration = id .. '|' .. ARGV[1]
        redis.call('HSET', KEYS[2], ARGV[i], declaration)
        redis.call('SADD', KEYS[3], id % shards)
    end
    declarations[#declarations + 1] = declaration
end
-- The registry expires if the campaign is never closed, for example when all the factories crash.
for k = 1, 3 do
    redis.call('EXPIRE', KEYS[k], ARGV[2])
end
return { declarations, redis.call('SMEMBERS', KEYS[3]) }
