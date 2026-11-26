-- Flushes the meter deltas of a factory for the meter levels of a single shard, acknowledges the period when required
-- and, when all the factories acknowledged it, publishes the values of the period to the owners of the shard.
--
-- The values of each meter level are stored as a single JSON object by short id (the histograms as separate entries
-- with the suffix ":b"), in order to limit the count of Redis commands to a few per call, whatever the count of
-- meters. In a JSON object, the numbers are summed, except "max" and "mx" which keep the highest value and "val"
-- (gauge) which is summed but published as is; the nested objects (hits by interval) and the histograms are merged
-- by key.
--
-- The flushes only update the cumulative values. The values of the period are only computed when it is complete, as
-- the difference between the cumulative values and the copy of them made at the end of the previous period. The max
-- of the current period is tracked in the fields "pm" (max) and "pp" (period).
--
-- All the keys share the hash tag of the shard, so that the script never accesses several slots.
-- KEYS[1]: hash of the cumulative values of the meter levels of the shard, by short id
-- KEYS[2]: hash of the cumulative values at the end of the previous period, by short id
-- KEYS[3]: set of the factories that acknowledged the period
-- KEYS[4]: set of the factories that completed the campaign
-- KEYS[5]: key of the fallback copy of the message of the period
--
-- ARGV[1]: identifier of the flushing factory
-- ARGV[2]: count of factories running the campaign
-- ARGV[3]: acknowledgement mode: 0 = none, 1 = end of the period, 2 = end of the campaign
-- ARGV[4]: end of the period, in epoch seconds
-- ARGV[5]: channel of the shard, to publish the values of the period
-- ARGV[6]: TTL of the keys, in seconds
-- ARGV[7]: command to publish the message: SPUBLISH (sharded Pub/Sub) or PUBLISH
-- ARGV[8]: logarithm of the gamma of the histograms
-- ARGV[9]: duration in milliseconds after which the hits of a throughput are folded
-- ARGV[10]: JSON object of the deltas by entry, an empty object meaning that only the cumulative values are expected
--
-- Returns: { 1 if the period was published else 0, JSON object of the cumulative values of the flushed entries }

-- Maximal count of fields per HMGET / HSET, to keep the count of arguments of a Redis command bounded.
local BATCH = 500
-- Width of a bucket of the logarithmic histograms, the same as LogHistogram.LOG_GAMMA in the factories.
local log_gamma = tonumber(ARGV[8])
-- Age in milliseconds, relative to the latest interval, from which the hits of a throughput are folded.
local folding_delay = tonumber(ARGV[9])
-- End of the period of the flush, in epoch seconds.
local current_period = tonumber(ARGV[4])

-- Merges the decoded JSON object [delta] of a meter level into its cumulative values [target], in place:
-- - nested objects (hits by interval "h", histogram buckets) are merged by key, summing the counts,
-- - "max" and "mx" keep the highest value,
-- - the other numbers are summed.
-- When [period] is given, the max of the current period is tracked as well: "pm" holds the highest "max" received in
-- the period "pp", and is reset by the first value of a new period.
--
-- Example: merging {"cnt": 2, "max": 7} into {"cnt": 10, "max": 9, "pm": 5, "pp": 1700000005} in the same period
-- gives {"cnt": 12, "max": 9, "pm": 7, "pp": 1700000005}.
local function merge(target, delta, period)
    for field, value in pairs(delta) do
        if type(value) == 'table' then
            local values = target[field] or {}
            for key, count in pairs(value) do
                values[key] = (values[key] or 0) + count
            end
            target[field] = values
        elseif field == 'max' or field == 'mx' then
            if target[field] == nil or value > target[field] then
                target[field] = value
            end
            if field == 'max' and period ~= nil then
                if target.pp ~= period or value > target.pm then
                    target.pm = value
                end
                target.pp = period
            end
        else
            target[field] = (target[field] or 0) + value
        end
    end
end

-- Folds the hits of the old intervals of a throughput into the count of intervals "n", the sum "hs", the max "mx"
-- and the histogram "b", in place, so that the size of the values of a throughput does not grow with the duration of
-- the campaign. An interval is old when it starts more than [folding_delay] milliseconds before the latest interval:
-- it does not receive hits anymore and was already part of a published period.
--
-- The bucket of a folded interval is computed like LogHistogram.bucketOf: "z" for 0 hits, else
-- ceil(ln(hits) / log_gamma).
--
-- Example with a folding delay of 10000 ms: {"h": {"1000": 4, "15000": 6}} becomes
-- {"h": {"15000": 6}, "n": 1, "hs": 4, "mx": 4, "b": {"70": 1}}.
local function fold(values)
    local latest
    for interval in pairs(values.h) do
        local start = tonumber(interval)
        if latest == nil or start > latest then
            latest = start
        end
    end
    for interval, hits in pairs(values.h) do
        if tonumber(interval) < latest - folding_delay then
            values.n = (values.n or 0) + 1
            values.hs = (values.hs or 0) + hits
            if values.mx == nil or hits > values.mx then
                values.mx = hits
            end
            local bucket = 'z'
            if hits > 0 then
                bucket = tostring(math.ceil(math.log(hits) / log_gamma))
            end
            values.b = values.b or {}
            values.b[bucket] = (values.b[bucket] or 0) + 1
            values.h[interval] = nil
        end
    end
end

-- Returns the values of the fields [ids] of the hash [key], in the order of [ids] (false for a missing field), with
-- one HMGET per batch of BATCH fields.
local function hmget(key, ids)
    local result = {}
    for i = 1, #ids, BATCH do
        local values = redis.call('HMGET', key, unpack(ids, i, math.min(i + BATCH - 1, #ids)))
        for _, value in ipairs(values) do
            result[#result + 1] = value
        end
    end
    return result
end

-- Sets the fields [ids] of the hash [key] to the values at the same index in [encoded], with one HSET per batch of
-- BATCH fields.
local function hset(key, ids, encoded)
    for i = 1, #ids, BATCH do
        local arguments = {}
        for j = i, math.min(i + BATCH - 1, #ids) do
            arguments[#arguments + 1] = ids[j]
            arguments[#arguments + 1] = encoded[j]
        end
        redis.call('HSET', key, unpack(arguments))
    end
end

-- Adds the deltas to the cumulative values and keeps the encoded result of each entry, returned to the factory to
-- refresh its cache.
local deltas = cjson.decode(ARGV[10])
local ids = {}
for id in pairs(deltas) do
    ids[#ids + 1] = id
end

local cumulated = {}
if #ids > 0 then
    local cumulative_values = hmget(KEYS[1], ids)
    local encoded_cumulative = {}
    for i, id in ipairs(ids) do
        local cumulative = cumulative_values[i] and cjson.decode(cumulative_values[i]) or {}
        merge(cumulative, deltas[id], current_period)
        if cumulative.h ~= nil then
            fold(cumulative)
        end
        encoded_cumulative[i] = cjson.encode(cumulative)
        cumulated[#cumulated + 1] = '"' .. id .. '":' .. encoded_cumulative[i]
    end
    hset(KEYS[1], ids, encoded_cumulative)
end

-- Fields of a meter level that are not computed as a difference with the previous period: the max and the gauge
-- value are published as they are, the fields tracking the max of the period are internal, and the folded values of
-- a throughput only matter for the whole campaign.
local SKIPPED = { pm = true, pp = true, max = true, val = true, n = true, hs = true, mx = true, b = true }

-- Returns the values of the period of a meter level: the difference between its [cumulative] values and their copy
-- [previous] at the end of the previous period. [is_histogram] is true for a histogram entry (":b" suffix), whose
-- fields are all bucket counts.
-- - The nested objects not skipped (hits by interval) are compared by key, keeping the keys that changed.
-- - The numbers not skipped are compared directly; for a histogram, the buckets without change are omitted.
-- - The gauge value "val" is the cumulative one, and "max" is the max of the period when the level received values
--   in the current period.
--
-- Example: {"cnt": 12, "max": 9, "pm": 7, "pp": 1700000005} with the previous copy {"cnt": 10} gives
-- {"cnt": 2, "max": 7} for the period 1700000005.
local function period_values(cumulative, previous, is_histogram)
    local result = {}
    for field, value in pairs(cumulative) do
        if type(value) == 'table' and not SKIPPED[field] then
            local before = previous[field] or {}
            local values = {}
            for key, count in pairs(value) do
                local delta = count - (before[key] or 0)
                if delta ~= 0 then
                    values[key] = delta
                end
            end
            result[field] = values
        elseif type(value) == 'table' then
            -- Folded values, not relevant for a period.
        elseif is_histogram then
            local delta = value - (previous[field] or 0)
            if delta ~= 0 then
                result[field] = delta
            end
        elseif not SKIPPED[field] then
            result[field] = value - (previous[field] or 0)
        end
    end
    if not is_histogram then
        result.val = cumulative.val
        if cumulative.pp == current_period then
            result.max = cumulative.pm
        end
    end
    return result
end

-- Builds a JSON object from the flat list [field 1, JSON value 1, field 2, JSON value 2, ...] returned by HGETALL,
-- without decoding the values: {"field 1": value 1, "field 2": value 2}.
local function concatenate(flat_values)
    local parts = {}
    for i = 1, #flat_values, 2 do
        parts[#parts + 1] = '"' .. flat_values[i] .. '":' .. flat_values[i + 1]
    end
    return '{' .. table.concat(parts, ',') .. '}'
end

-- Acknowledgement and barrier: when all the factories acknowledged the period or completed the campaign, the values
-- of the period of all the levels of the shard are published, with the cumulative values when all the factories
-- completed the campaign, a fallback copy is kept, the current values become the previous copy for the next period,
-- and the acknowledgements are reset.
local published = 0
if ARGV[3] ~= '0' then
    if ARGV[3] == '2' then
        redis.call('SADD', KEYS[4], ARGV[1])
    else
        redis.call('SADD', KEYS[3], ARGV[1])
    end
    local factories_count = tonumber(ARGV[2])
    -- The factories that completed the campaign acknowledge all the later periods.
    if #redis.call('SUNION', KEYS[3], KEYS[4]) >= factories_count then
        local final = redis.call('SCARD', KEYS[4]) >= factories_count
        local all_cumulative = redis.call('HGETALL', KEYS[1])
        local previous_ids = {}
        for i = 1, #all_cumulative, 2 do
            previous_ids[#previous_ids + 1] = all_cumulative[i]
        end
        local previous_values = #previous_ids > 0 and hmget(KEYS[2], previous_ids) or {}
        local parts = {}
        for i = 1, #all_cumulative, 2 do
            local id = all_cumulative[i]
            local previous = previous_values[(i + 1) / 2]
            local previous_level = previous and cjson.decode(previous) or {}
            local is_histogram = string.find(id, ':b', 1, true) ~= nil
            local values = period_values(cjson.decode(all_cumulative[i + 1]), previous_level, is_histogram)
            parts[#parts + 1] = '"' .. id .. '":' .. cjson.encode(values)
        end
        local message = '{"v":3,"per":' .. ARGV[4] .. ',"fin":' .. tostring(final) .. ',"m":{' .. table.concat(parts, ',') .. '}'
        if final then
            message = message .. ',"cum":' .. concatenate(all_cumulative)
        end
        message = message .. '}'
        if #all_cumulative > 0 then
            for i = 1, #all_cumulative, BATCH * 2 do
                redis.call('HSET', KEYS[2], unpack(all_cumulative, i, math.min(i + BATCH * 2 - 1, #all_cumulative)))
            end
        end
        redis.call(ARGV[7], ARGV[5], message)
        redis.call('SET', KEYS[5], message, 'EX', ARGV[6])
        redis.call('DEL', KEYS[3])
        published = 1
    end
end
-- The TTL is refreshed at least once per period, by the acknowledgements.
if ARGV[3] ~= '0' then
    for k = 1, 4 do
        redis.call('EXPIRE', KEYS[k], ARGV[6])
    end
end
return { published, '{' .. table.concat(cumulated, ',') .. '}' }
