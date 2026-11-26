-- Combines the counts of transitions of an evolution verification step over all its instances (one per factory).
--
-- The counts are stored in the hash KEYS[1], with the fields:
-- - valid: count of transitions evolving in the expected direction,
-- - invalid: count of transitions evolving in the other direction,
-- - instances: count of registered instances of the step.
--
-- ARGV[1]: operation, one of:
-- - register: adds an instance; returns an empty array;
-- - increment: adds the deltas ARGV[3] (valid) and ARGV[4] (invalid); returns { valid, invalid };
-- - unregister: adds the deltas as for increment and removes an instance; returns { valid, invalid, instances }.
--   The hash is deleted when no instance remains.
-- ARGV[2]: time to live of the hash in seconds, refreshed by each operation while it is kept.
--
-- The percentage is computed by the caller: Redis converts the numbers returned by Lua into integers.
--
-- Example, two factories:
--   register                → instances = 1
--   register                → instances = 2
--   increment 990 10        → { 990, 10 }        (99 % valid)
--   unregister 5 0          → { 995, 10, 1 }     (not the last instance)
--   unregister 0 0          → { 995, 10, 0 }     (last instance, the hash is deleted)

local key = KEYS[1]
local operation = ARGV[1]
local ttl = ARGV[2]

if operation == 'register' then
    redis.call('HINCRBY', key, 'instances', 1)
    redis.call('EXPIRE', key, ttl)
    return {}
end

local valid = redis.call('HINCRBY', key, 'valid', ARGV[3])
local invalid = redis.call('HINCRBY', key, 'invalid', ARGV[4])
if operation == 'increment' then
    redis.call('EXPIRE', key, ttl)
    return { valid, invalid }
end

local instances = redis.call('HINCRBY', key, 'instances', -1)
if instances > 0 then
    redis.call('EXPIRE', key, ttl)
else
    redis.call('DEL', key)
end
return { valid, invalid, instances }
