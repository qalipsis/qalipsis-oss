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

import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.MeasurementMetric
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.meters.Statistic
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.BUCKETS_FIELD
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.COUNT_FIELD
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.DENOMINATOR_FIELD
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.MAX_FIELD
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.NUMERATOR_FIELD
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.TOTAL_FIELD
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.VALUE_FIELD

/**
 * Converts the combined values of a meter level, as stored in Redis, into the measurements of its snapshots, with the
 * same statistics as the in-memory meters.
 */
internal object RedisMeterMeasurements {

    /**
     * Creates the measurements of [level] from its [values], or null when there is nothing to publish.
     * The missing values are considered as 0, except for the gauges, which keep their [latestGaugeValue].
     */
    @Suppress("UNCHECKED_CAST")
    fun measurements(level: RedisMeterLevel, values: Map<String, Any>?, latestGaugeValue: Double?): List<Measurement>? {
        val fields = values.orEmpty()
        fun number(field: String) = (fields[field] as Number?)?.toDouble() ?: 0.0
        fun percentiles() = (fields[BUCKETS_FIELD] as Map<String, Double>?).orEmpty().let { buckets ->
            level.percentiles.map { DistributionMeasurementMetric(LogHistogram.percentile(buckets, it), Statistic.PERCENTILE, it) }
        }
        fun mean() = if (number(COUNT_FIELD) == 0.0) 0.0 else number(TOTAL_FIELD) / number(COUNT_FIELD)

        return when (level.meterId.type) {
            MeterType.COUNTER -> listOf(MeasurementMetric(number(COUNT_FIELD), Statistic.COUNT))
            MeterType.GAUGE -> ((fields[VALUE_FIELD] as Number?)?.toDouble() ?: latestGaugeValue)
                ?.let { listOf(MeasurementMetric(it, Statistic.VALUE)) }

            MeterType.RATE -> listOf(
                MeasurementMetric(number(DENOMINATOR_FIELD).let { if (it == 0.0) 0.0 else number(NUMERATOR_FIELD) / it }, Statistic.VALUE)
            )

            MeterType.TIMER -> listOf(
                MeasurementMetric(mean(), Statistic.MEAN),
                MeasurementMetric(number(COUNT_FIELD), Statistic.COUNT),
                MeasurementMetric(number(TOTAL_FIELD), Statistic.TOTAL_TIME),
                MeasurementMetric(number(MAX_FIELD), Statistic.MAX),
            ) + percentiles()

            MeterType.DISTRIBUTION_SUMMARY -> listOf(
                MeasurementMetric(mean(), Statistic.MEAN),
                MeasurementMetric(number(COUNT_FIELD), Statistic.COUNT),
                MeasurementMetric(number(TOTAL_FIELD), Statistic.TOTAL),
                MeasurementMetric(number(MAX_FIELD), Statistic.MAX),
            ) + percentiles()

            MeterType.THROUGHPUT -> RedisThroughput.stats(fields, level.intervalMillis, null).let { stats ->
                listOf(
                    MeasurementMetric(stats.current, Statistic.VALUE),
                    MeasurementMetric(stats.mean, Statistic.MEAN),
                    MeasurementMetric(stats.max, Statistic.MAX),
                    MeasurementMetric(number(TOTAL_FIELD), Statistic.TOTAL),
                ) + level.percentiles.map { DistributionMeasurementMetric(stats.percentile(it), Statistic.PERCENTILE, it) }
            }

            else -> null
        }
    }

    const val BUCKETS_SUFFIX = ":b"

    /**
     * Groups the entries stored in Redis by short id of meter level: the histogram of a level, stored as a separate
     * entry with the suffix [BUCKETS_SUFFIX], is added to the values of its level in the field [BUCKETS_FIELD].
     */
    fun byLevel(entries: Map<String, Map<String, Any?>>): Map<Long, Map<String, Any>> {
        val result = HashMap<Long, MutableMap<String, Any>>()
        entries.forEach { (key, values) ->
            if (key.endsWith(BUCKETS_SUFFIX)) {
                result.getOrPut(key.removeSuffix(BUCKETS_SUFFIX).toLong()) { HashMap() }[BUCKETS_FIELD] = normalize(values)
            } else {
                result.getOrPut(key.toLong()) { HashMap() }.putAll(normalize(values))
            }
        }
        return result
    }

    /**
     * Converts the numbers parsed from JSON into doubles, in maps of values by field.
     */
    @Suppress("UNCHECKED_CAST")
    fun normalize(values: Map<String, Any?>): Map<String, Any> = values.mapNotNull { (field, value) ->
        when (value) {
            is Number -> field to value.toDouble()
            is Map<*, *> -> field to (value as Map<String, Any?>).mapValues { (it.value as Number).toDouble() }
            else -> null
        }
    }.toMap()
}
