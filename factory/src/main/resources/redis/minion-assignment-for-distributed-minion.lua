-- Assigned available DAGs of minions to a factory using a strategy considering the DAG supported by each factory.
-- Prerequisites: the factory has a set containing the DAGs that were assigned to it.
-- Example of result:
-- 1) "evaluated-minions-count"
-- 2) (integer) 2
-- 3) "assignments"
-- 4) 1) "minion-4"
--    2) 1) "dag-5"
--       2) "dag-4"
--       3) "dag-3"
--    3) "minion-3"
--    4) 1) "dag-5"
--       2) "dag-4"
--       3) "dag-3"

-- The script expects the following keys:
-- 1. The key of the set containing the IDs of the minions
-- 2. The key of the set containing the DAGs of the factory
-- 3. The hash key containing the factory channel name owning the root, by minion

-- The script expects the following arguments:
-- 1. The name of the channel assigned to the factory
-- 2. The root DAG underload
-- 3. The size of the random minions to be evaluated
-- 4. The prefix for the keys of the sets storing the unassigned DAGs of a minion
-- 5. The prefix for the keys of the hash storing the assigned DAGs of a minion
-- 7. The maximal number of minions that can be assigned in that batch

-- Resources:
-- - https://redis.io/commands/eval
-- - https://redis.io/commands/eval#atomicity-of-scripts

local minionsIdsList = KEYS[1]
local factoryDagsList = KEYS[2]
local minionRootDagMinionKey = KEYS[3]

local factoryChannelName = ARGV[1]
local rootDagUnderload = ARGV[2]
local randomEvaluationSize = tonumber(ARGV[3])
local minionUnassignedDagsKeyPrefix = ARGV[4]
local minionAssignedDagsKeyPrefix = ARGV[5]
local maxMinionsCount = tonumber(ARGV[6])

-- Tries the assignment of DAGs of a minion to the factory and returns a table with their count and IDs, 
local function evaluateAssignment(minionId)
    -- Key of the unassigned DAGs of the minion.
    local unassignedDagsKey = minionUnassignedDagsKeyPrefix .. minionId
    -- For the given minion, extracts the DAGs that can be assigned by intersecting the sets of unassigned DAGs of the minion
    -- with the DAGs supported by the factory.
    local assignable = redis.call('sinter', unassignedDagsKey, factoryDagsList)
    local assignableCount = #assignable
    if assignableCount > 0 then
        redis.call('srem', unassignedDagsKey, unpack(assignable))
        -- If DAGs were assigned, they are removed from the set of unassigned DAGs of the minion.
        local remainingDagsCount = redis.call('scard', unassignedDagsKey)
        if remainingDagsCount == 0 then
            -- If there is no more DAG to assign, the minion is removed from the list of remaining minions.
            redis.call('srem', minionsIdsList, minionId)
        end

        -- Attach the factory channel name for each DAG of the minion.
        local assignedDagsKey = minionAssignedDagsKeyPrefix .. minionId
        local keyValues = {}
        for _, assignedDag in pairs(assignable) do
            table.insert(keyValues, assignedDag)
            table.insert(keyValues, factoryChannelName)

            -- If the DAG is the root under load, the relationship with the factory is saved.
            if assignedDag == rootDagUnderload then
                redis.call('hmset', minionRootDagMinionKey, minionId, factoryChannelName)
            end
        end
        redis.call('hmset', assignedDagsKey, unpack(keyValues))
        redis.call('hincrby', assignedDagsKey, 'scheduled-dags', assignableCount)
        redis.call('hincrby', assignedDagsKey, 'remaining-dags', assignableCount)
    end

    local result = {}
    result['count'] = assignableCount
    result['dags'] = assignable
    return result
end


-- Gets a random set of distinct minions to verify if they can be assigned to the current factory.
local randMinions = redis.call('srandmember', minionsIdsList, randomEvaluationSize)
local evaluated = #randMinions
local assignments = {}

local assigned = 0
if evaluated > 0 then
    for _, minionId in pairs(randMinions) do
        local minionAssignment = evaluateAssignment(minionId)
        if minionAssignment['count'] > 0 then
            table.insert(assignments, minionId)
            table.insert(assignments, minionAssignment['dags'])

            -- Stop the evaluation if the maximal count of minions is reached.
            assigned = assigned + 1
            if assigned >= maxMinionsCount then
                break
            end
        end
    end
end

-- Returns the number of minions evaluated. When 0, it means that the assignment is now complete.
return { 'evaluated-minions-count', evaluated, 'assignments', assignments }

