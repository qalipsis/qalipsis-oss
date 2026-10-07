-- Schedule random minions with a start offset.

-- Example of result:
-- 1) "scheduled-minions-count"
-- 2) (integer) 2
-- 1) "unscheduled-minions"
-- 2) (integer) 0

-- The script expects the following keys:
-- 1. The prefix for the scenario keys

-- Resources:
-- - https://redis.io/commands/eval
-- - https://redis.io/commands/eval#atomicity-of-scripts

local minionsRootFactoriesKey = KEYS[1] .. 'minion:root-factory-channel'
local unscheduledMinionsKey = KEYS[1] .. 'minion:unscheduled'
local startingLinesKey = KEYS[1] .. 'minion:starting-lines'
local scheduledMinionsByFactoryKey = KEYS[1] .. 'minion:scheduled-by-factory:'
local schedulingExecutionKey = KEYS[1] .. 'minion:scheduling-executed'

local function schedule()
    local scheduledMinionsCount = 0

    local scanCursor = 0
    repeat
        local startingLinesKeyScanResult = redis.call('hscan', startingLinesKey, scanCursor, 'COUNT', 50)
        scanCursor = tonumber(startingLinesKeyScanResult[1])
        local startingLines = startingLinesKeyScanResult[2]

        for i = 1, #startingLines, 2
        do
            local offset = startingLines[i]
            local minionsCount = startingLines[i + 1]

            local minions = redis.call('hrandfield', unscheduledMinionsKey, minionsCount, 'WITHVALUES')
            scheduledMinionsCount = scheduledMinionsCount + #minions
            if #minions > 0 then
                for j = 1, #minions, 2
                do
                    local minion = minions[j]
                    local factoryChannel = minions[j + 1]

                    -- Add the minion to the schedule of the factory owning its root.
                    redis.call('hset', scheduledMinionsByFactoryKey .. factoryChannel, minion, offset)
                    redis.call('hdel', unscheduledMinionsKey, minion)
                end
            else
                -- Returns the number of scheduled minions
                return scheduledMinionsCount
            end
        end

    until (scanCursor == 0)

    -- Returns the number of scheduled minions
    return scheduledMinionsCount
end

-- All the factories executing the scenario request the scheduling, but the starting lines always cover all its
-- minions. Only the first request is therefore executed, the further ones would reschedule minions that are
-- already planned, potentially after they were started.
if redis.call('setnx', schedulingExecutionKey, '1') == 0 then
    redis.call('unlink', startingLinesKey)
    return { 'scheduled-minions-count', 0, 'unscheduled-minions', 0 }
end

redis.call('copy', minionsRootFactoriesKey, unscheduledMinionsKey)
local scheduledMinions = schedule()
-- Count the number of unscheduled minions.
local unscheduledMinionsCount = redis.call('hlen', unscheduledMinionsKey)

redis.call('unlink', unscheduledMinionsKey, startingLinesKey)

-- Returns the number of minions evaluated. When 0, it means that the assignment is now complete.
return { 'scheduled-minions-count', scheduledMinions, 'unscheduled-minions', unscheduledMinionsCount }

