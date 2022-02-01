-- Register a set of minions to assign on a scenario.

-- The script expects the following keys:
-- 1. The key of the set containing all the IDs of the new minions to register
-- 2. The key of the set of the not yet assigned minions of the scenario
-- 3. The key of the set containing the minions underload of the scenario
-- 4. The prefix of the keys for the sets containing the DAGs of each minion
-- 5. The key of the hash containing the counters
-- 6. The key of the hash containing the counters for the singletons

-- The script expects the following arguments:
-- 1. The ID of the scenario
-- 2. The comma-separated list of the DAG IDs
-- 3. The flag indicating whether the minions are under load (true) or not (false)

-- Resources:
-- - https://redis.io/commands/eval
-- - https://redis.io/commands/eval#atomicity-of-scripts

local minionsSet = KEYS[1]
local unassignedMinions = KEYS[2]
local underLoadMinions = KEYS[3]
local keyPrefixForUnassignedDags = KEYS[4]
local counters = KEYS[5]
local singletonRegistry = KEYS[6]

local scenarioId = ARGV[1]
local dagIds = {}
for dagId in string.gmatch(ARGV[2], '([^,]+)') do
    table.insert(dagIds, dagId)
end
local stringToBoolean={['true']=true, ['false']=false}
local underLoad = stringToBoolean[ARGV[3]]

local unpackSize = 6000 -- Maximal size accepted to unpack in a Redis command.
local allMinions = {}

-- Reads the full list of minions to process. The Set is temporary and can be immediately deleted.
local minions = redis.call('srandmember', minionsSet, unpackSize)
-- Updates the set of all the unassigned minions of the scenario.
while(#minions > 0) do
  for _, minion in pairs(minions) do
    table.insert(allMinions, minion)
  end
  redis.call('srem', minionsSet, unpack(minions))
  redis.call('sadd', unassignedMinions, unpack(minions))

  if underLoad then
    redis.call('sadd', underLoadMinions, unpack(minions))
  end

  minions = redis.call('srandmember', minionsSet, unpackSize)
end

-- For each minion, creates the set containing the dags it has to execute.
for _, minion in pairs(allMinions) do
    redis.call('sadd', keyPrefixForUnassignedDags..minion, unpack(dagIds))
end

if underLoad then
  -- Updates the counter of minions by scenarios.
  redis.call('hincrby', counters, scenarioId, #allMinions)
else
  -- Updates the counters of DAGS for the singleton.
  for _, minion in pairs(allMinions) do
    redis.call('hset', singletonRegistry, scenarioId .. '-' .. minion, '1')
  end
end

return {'1'}