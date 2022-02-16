-- Returns the channels to use to transport the step context to a remote factory.

-- Example of result:
-- TODO

-- The script expects the following keys:
-- 1. The prefix for the keys of the hash storing the assigned DAGs of a minion

-- The script expects the following arguments:
-- 1. The comma-separated list of the minions IDs
-- 2. The comma-separated list of the DAGs IDs

-- Resources:
-- - https://redis.io/commands/eval
-- - https://redis.io/commands/eval#atomicity-of-scripts

local minionAssignedDagsKeyPrefix = KEYS[1]

local minionsIds = {}
for minionId in string.gmatch(ARGV[1], '([^,]+)') do
    table.insert(minionsIds, minionId)
end
local dagsIds = {}
for dagId in string.gmatch(ARGV[2], '([^,]+)') do
    table.insert(dagsIds, dagId)
end

local result = {}
for _, minionId in pairs(minionsIds) do
    local assignedDagsKey = minionAssignedDagsKeyPrefix .. minionId
    local assignments = redis.call('hmget', assignedDagsKey, unpack(dagsIds))
    table.insert(result, minionId)
    table.insert(result, assignments)
end

return result

