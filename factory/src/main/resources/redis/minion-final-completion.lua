-- Marks a minion as definitely complete, when the completion of its DAGs was already verified by its factory,
-- and verifies whether its scenario and the campaign are now complete.
-- Example of result:
-- 1) "scenario-complete"
-- 2) (integer) 0
-- 3) "campaign-complete"
-- 4) (integer) 0

-- The script expects the following keys:
-- 1. The key of the hash containing the counters
-- 2. The key of the hash storing the assigned DAGs of the minion
-- 3. The key of the hash for the singleton registry

-- The script expects the following arguments:
-- 1. The ID of the scenario

-- Resources:
-- - https://redis.io/commands/eval
-- - https://redis.io/commands/eval#atomicity-of-scripts

local counters = KEYS[1]
local minionAssignedDags = KEYS[2]
local singletonRegistry = KEYS[3]

local scenarioName = ARGV[1]

local completedScenario = 0
local completedCampaign = 0

redis.call('unlink', minionAssignedDags)
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

return { 'scenario-complete', completedScenario, 'campaign-complete', completedCampaign }
