/*
 * QALIPSIS
 * Copyright (C) 2026 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
 *
 */

package io.qalipsis.core.factory.redis.meters

import io.qalipsis.api.meters.Meter

/**
 * Level of a meter (scenario + zone, scenario or campaign), combined in Redis over all the factories of a campaign.
 *
 * The values of a level are fields, either numbers or maps of numbers (histogram buckets, hits by interval).
 * The numbers are summed when merged, except [MAX_FIELD] which keeps the highest value; the maps are merged by key.
 * The same rules are applied by the Lua script in Redis.
 *
 * The deltas of the local meters are drained into [pending] by the flush, which is the only accessor of it.
 * [cumulative] caches the combined values returned by Redis for the meter level.
 *
 * @property meterId identifier of the meter level, with the tags of the level
 * @property percentiles percentiles to publish, for the distributions
 * @property intervalMillis duration of the intervals of a throughput, in milliseconds
 */
internal class RedisMeterLevel(
    val meterId: Meter.Id,
    val percentiles: Collection<Double> = emptyList(),
    val intervalMillis: Long = 0,
) {

    val key: String = buildKey(meterId)

    /**
     * Short id of the meter level in the campaign, set once declared.
     */
    @Volatile
    var shortId: Long = UNDECLARED

    /**
     * Factory owning the meter level and publishing its values, set once declared.
     */
    @Volatile
    var owner: String = ""

    /**
     * Shard of the meter level in Redis, set once declared.
     */
    @Volatile
    var shard: Int = 0

    val isDeclared: Boolean
        get() = shortId != UNDECLARED

    /**
     * True when the level is the one of a meter, whose combined values are read by the meter: it is flushed
     * frequently, while the other levels are only flushed at the end of each period, for the snapshots.
     */
    @Volatile
    var isPrimary: Boolean = false

    /**
     * Deltas not yet flushed into Redis, by field. Only accessed by the flush.
     */
    val pending = mutableMapOf<String, Any>()

    /**
     * Combined cumulative values over all the factories, by field, as returned by the latest flush.
     */
    @Volatile
    var cumulative: Map<String, Any> = emptyMap()

    /**
     * Adds the numeric [delta] of [field] to the pending values, merged with the pending delta of the same field:
     * summed, except for [MAX_FIELD], whose highest value is kept. For example, adding 3 then 2 to `"tot"` leaves
     * `"tot": 5` pending, adding 7 then 4 to `"max"` leaves `"max": 7`.
     *
     * The non-finite deltas are ignored, as well as the deltas of 0 that change nothing; a max of 0 is kept, since it
     * tells that something was recorded.
     */
    fun addPending(field: String, delta: Double) {
        // A max is only relevant when something was recorded, the other values when they change something.
        if (delta.isFinite() && (delta != 0.0 || field == MAX_FIELD)) {
            pending[field] = merge(field, pending[field] as Double?, delta)
        }
    }

    /**
     * Adds the [deltas] of the map [field] (buckets of a histogram, hits by interval) to the pending values, summed by
     * key: adding `{"35": 2}` then `{"35": 1, "231": 1}` to `"b"` leaves `"b": {"35": 3, "231": 1}` pending.
     * Nothing is added when [deltas] is empty.
     */
    @Suppress("UNCHECKED_CAST")
    fun addPending(field: String, deltas: Map<String, Double>) {
        if (deltas.isNotEmpty()) {
            val values = pending.getOrPut(field) { HashMap<String, Double>() } as MutableMap<String, Double>
            deltas.forEach { (key, delta) -> values.merge(key, delta, Double::plus) }
        }
    }

    /**
     * Adds again all the [values] of a flush that failed, numbers and maps, so that they are sent with the next flush
     * instead of being lost. The fields of other types are ignored.
     */
    @Suppress("UNCHECKED_CAST")
    fun addPending(values: Map<String, Any>) = values.forEach { (field, value) ->
        when (value) {
            is Number -> addPending(field, value.toDouble())
            is Map<*, *> -> addPending(field, value as Map<String, Double>)
        }
    }

    /**
     * Combined cumulative value of the numeric [field] over all the factories, as returned by the latest flush, or 0
     * when not known yet. It does not contain the local values not flushed yet.
     */
    fun cumulative(field: String): Double = (cumulative[field] as Number?)?.toDouble() ?: 0.0

    /**
     * Combined cumulative values of the map [field] over all the factories, as returned by the latest flush, for
     * example the buckets of a histogram `{"35": 3, "231": 1}`, or an empty map when not known yet.
     */
    @Suppress("UNCHECKED_CAST")
    fun cumulativeMap(field: String): Map<String, Double> = cumulative[field] as Map<String, Double>? ?: emptyMap()

    override fun toString() = "RedisMeterLevel(meterId=$meterId, shortId=$shortId, owner='$owner', shard=$shard)"

    companion object {

        const val UNDECLARED = -1L

        const val MAX_FIELD = "max"

        private const val SEPARATOR = '\u001F'

        private const val TAG_SEPARATOR = '\u001E'

        fun merge(field: String, current: Double?, delta: Double): Double = when {
            current == null -> delta
            field == MAX_FIELD -> maxOf(current, delta)
            else -> current + delta
        }

        /**
         * Builds a key independent of the order of the tags, using control characters unlikely in names and tags.
         */
        fun buildKey(meterId: Meter.Id) = buildString {
            append(meterId.type.name).append(SEPARATOR).append(meterId.meterName)
            meterId.tags.toSortedMap().forEach { (name, value) ->
                append(SEPARATOR).append(name).append(TAG_SEPARATOR).append(value)
            }
        }
    }
}
