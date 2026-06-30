/*
 * QALIPSIS
 * Copyright (C) 2026 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.factory.meters

import com.fasterxml.jackson.databind.ObjectMapper
import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.meters.Statistic
import io.qalipsis.core.meters.CampaignMeterSnapshot
import java.lang.Double.isFinite
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

internal object CampaignMeterConverter {

    fun convert(snapshot: MeterSnapshot, objectMapper: ObjectMapper): CampaignMeterSnapshot {
        val meterId = snapshot.meterId
        var tenant: String? = null
        var campaign: String? = null
        var scenario: String? = null
        val remainingTags = mutableMapOf<String, String>()
        meterId.tags.forEach { (key, value) ->
            when (key) {
                "tenant" -> tenant = value
                "campaign" -> campaign = value
                "scenario" -> scenario = value
                else -> remainingTags[key.lowercase()] = value
            }
        }
        val serializedTags = remainingTags.takeIf { it.isNotEmpty() }?.let {
            objectMapper.writeValueAsString(it.toSortedMap())
        }

        val base = CampaignMeterSnapshot(
            name = meterId.meterName,
            timestampEpochMs = snapshot.timestamp.toEpochMilli(),
            type = meterId.type.value,
            tags = serializedTags,
            tenant = tenant,
            campaign = campaign,
            scenario = scenario
        )
        return when (meterId.type) {
            MeterType.COUNTER -> convertCounter(snapshot.measurements, base)
            MeterType.GAUGE -> convertGauge(snapshot.measurements, base)
            MeterType.TIMER -> convertTimer(snapshot.measurements, base, objectMapper)
            MeterType.DISTRIBUTION_SUMMARY -> convertSummary(snapshot.measurements, base, objectMapper)
            MeterType.RATE -> convertRate(snapshot.measurements, base)
            MeterType.THROUGHPUT -> convertThroughput(snapshot.measurements, base, objectMapper)
            else -> base
        }
    }

    private fun convertCounter(
        measurements: Collection<Measurement>,
        base: CampaignMeterSnapshot
    ): CampaignMeterSnapshot {
        measurements.forEach { if (isFinite(it.value)) return base.copy(count = it.value) }
        return base
    }

    private fun convertGauge(
        measurements: Collection<Measurement>,
        base: CampaignMeterSnapshot
    ): CampaignMeterSnapshot {
        measurements.forEach { if (isFinite(it.value)) return base.copy(value = it.value) }
        return base
    }

    private fun convertTimer(
        measurements: Collection<Measurement>,
        base: CampaignMeterSnapshot,
        objectMapper: ObjectMapper
    ): CampaignMeterSnapshot {
        val statToValue = mutableMapOf<String, Double>()
        val other = mutableMapOf<String, String>()
        measurements.forEach { measurement ->
            val key = measurement.statistic.value
            when (measurement) {
                is DistributionMeasurementMetric ->
                    other["${key}_${measurement.observationPoint}".lowercase()] =
                        BigDecimal(measurement.value).toPlainString()

                else -> statToValue[key] = measurement.value
            }
        }
        return base.copy(
            count = statToValue[Statistic.COUNT.value] ?: 0.0,
            sum = statToValue[Statistic.TOTAL_TIME.value] ?: 0.0,
            mean = statToValue[Statistic.MEAN.value] ?: 0.0,
            max = statToValue[Statistic.MAX.value] ?: 0.0,
            unit = "${TimeUnit.MICROSECONDS}",
            other = other.takeIf { it.isNotEmpty() }?.let { objectMapper.writeValueAsString(it.toSortedMap()) }
        )
    }

    private fun convertSummary(
        measurements: Collection<Measurement>,
        base: CampaignMeterSnapshot,
        objectMapper: ObjectMapper
    ): CampaignMeterSnapshot {
        val statToValue = mutableMapOf<String, Double>()
        val other = mutableMapOf<String, String>()
        measurements.forEach { measurement ->
            val key = measurement.statistic.value
            when (measurement) {
                is DistributionMeasurementMetric ->
                    other["${key}_${measurement.observationPoint}".lowercase()] =
                        BigDecimal(measurement.value).toPlainString()

                else -> statToValue[key] = measurement.value
            }
        }
        return base.copy(
            count = statToValue[Statistic.COUNT.value] ?: 0.0,
            sum = statToValue[Statistic.TOTAL.value] ?: 0.0,
            mean = statToValue[Statistic.MEAN.value] ?: 0.0,
            max = statToValue[Statistic.MAX.value] ?: 0.0,
            other = other.takeIf { it.isNotEmpty() }?.let { objectMapper.writeValueAsString(it.toSortedMap()) }
        )
    }

    private fun convertRate(
        measurements: Collection<Measurement>,
        base: CampaignMeterSnapshot
    ): CampaignMeterSnapshot {
        measurements.forEach { if (isFinite(it.value)) return base.copy(value = it.value) }
        return base
    }

    private fun convertThroughput(
        measurements: Collection<Measurement>,
        base: CampaignMeterSnapshot,
        objectMapper: ObjectMapper
    ): CampaignMeterSnapshot {
        val statToValue = mutableMapOf<String, Double>()
        val other = mutableMapOf<String, String>()
        measurements.forEach { measurement ->
            val key = measurement.statistic.value
            when (measurement) {
                is DistributionMeasurementMetric ->
                    other["${key}_${measurement.observationPoint}".lowercase()] =
                        BigDecimal(measurement.value).toPlainString()

                else -> statToValue[key] = measurement.value
            }
        }
        return base.copy(
            value = statToValue[Statistic.VALUE.value] ?: 0.0,
            sum = statToValue[Statistic.TOTAL.value] ?: 0.0,
            mean = statToValue[Statistic.MEAN.value] ?: 0.0,
            max = statToValue[Statistic.MAX.value] ?: 0.0,
            other = other.takeIf { it.isNotEmpty() }?.let { objectMapper.writeValueAsString(it.toSortedMap()) }
        )
    }
}
