-- Mark a minion as complete.
-- Prerequisites: the assignment of the minions to the factory was already done.
-- Example of result:
-- 1) "minion-complete"
-- 2) (integer) 1
-- 3) "scenario-complete"
-- 4) (integer) 0
-- 5) "campaign-complete"
-- 6) (integer) 0

-- The script expects the following keys:
-- 1. The key of the hash for the singleton registry
-- 2. The key of the hash containing the counters
-- 2. The key of the hash containing storing the assigned DAGs of the minion

-- The script expects the following arguments:
-- 1. The ID of the scenario
-- 2. The ID of the minion
-- 3. The count of DAGs that are complete

-- Resources:
-- - https://redis.io/commands/eval
-- - https://redis.io/commands/eval#atomicity-of-scripts

local singletonRegistry = KEYS[1]
local counters = KEYS[2]
local minionAssignedDag = KEYS[3]

local scenarioName = ARGV[1]
local minionId = ARGV[2]
local completeDagsCount = tonumber(ARGV[3])

local completedMinion = 0
local completedScenario = 0
local completedCampaign = 0

local deletedSingleton = redis.call('hdel', singletonRegistry, scenarioName .. '-' .. minionId)
if deletedSingleton > 0 then
  -- A singleton minion is complete, it does not affect the completion of the scenario.
  completedMinion = 1
else
  local remainingDagsForMinion = redis.call('hincrby', minionAssignedDag, 'scheduled-dags', -1 * completeDagsCount)
  if remainingDagsForMinion == 0 then
    -- The minion is complete, let's check if other minion run in the scenario.
    completedMinion = 1
    redis.call('unlink', minionAssignedDag)
    local remainingMinionsInScenario = redis.call('hincrby', counters, scenarioName, -1)
    if remainingMinionsInScenario == 0 then
      -- The scenario is complete, let's check if other scenarios run in the campaign.
      completedScenario = 1
      local remainingScenarios = redis.call('hincrby', counters, 'scenarios', -1)
      if remainingScenarios == 0 then
        -- The campaign is complete.
        completedCampaign = 1
        redis.call('unlink', counters, singletonRegistry)
      end
    end
  end
end

return {'minion-complete', completedMinion, 'scenario-complete', completedScenario, 'campaign-complete', completedCampaign}

