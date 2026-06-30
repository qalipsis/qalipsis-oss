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

package io.qalipsis.core.head.report.meters

import com.fasterxml.jackson.databind.ObjectMapper
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.query.AggregationQueryExecutionContext
import io.qalipsis.api.query.DataRetrievalQueryExecutionContext
import io.qalipsis.api.query.Page
import io.qalipsis.api.report.TimeSeriesDataProvider
import io.qalipsis.api.report.TimeSeriesMeter
import io.qalipsis.api.report.TimeSeriesRecord
import io.qalipsis.api.report.TimeSeriesValues
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.Duration
import java.time.Instant

internal abstract class AbstractCampaignTimeSeriesDataProvider(
    protected val objectMapper: ObjectMapper
) : TimeSeriesDataProvider {

    abstract override suspend fun retrieveCampaignMeters(
        tenant: String,
        campaignKeys: Collection<String>,
        scenarioNames: Collection<String>
    ): List<TimeSeriesMeter>

    @Suppress("UNCHECKED_CAST")
    protected fun convertR2dbcRow(row: Row, @Suppress("UNUSED_PARAMETER") metadata: RowMetadata): TimeSeriesMeter {
        val type = row.get("type", String::class.java)!!
        val timestamp = row.get("timestamp", Instant::class.java)!!
        val campaign = row.get("campaign", String::class.java)
        val scenario = row.get("scenario", String::class.java)
        val tags = row.get("tags", String::class.java)
            ?.let { objectMapper.readValue(it, Map::class.java) } as Map<String, String>?
        val count = row.get("count", BigDecimal::class.java)
        val other = (row.get("other", String::class.java)
            ?.let { objectMapper.readValue(it, Map::class.java) } as Map<String, String>?)
            ?.mapValues { BigDecimal(it.value) }

        return if (type == MeterType.TIMER.value.lowercase()) {
            TimeSeriesMeter(
                name = row.get("name", String::class.java)!!,
                type = type,
                timestamp = timestamp,
                campaign = campaign,
                scenario = scenario,
                tags = tags,
                count = count?.toLong(),
                sumDuration = row.get("sum", BigDecimal::class.java)?.toLong()?.times(1_000)?.let(Duration::ofNanos),
                maxDuration = row.get("max", BigDecimal::class.java)?.toLong()?.times(1_000)?.let(Duration::ofNanos),
                meanDuration = row.get("mean", BigDecimal::class.java)?.toLong()?.times(1_000)
                    ?.let(Duration::ofNanos),
                other = other
            )
        } else {
            TimeSeriesMeter(
                name = row.get("name", String::class.java)!!,
                type = type,
                timestamp = timestamp,
                campaign = campaign,
                scenario = scenario,
                tags = tags,
                count = count?.toLong(),
                sum = row.get("sum", BigDecimal::class.java),
                mean = row.get("mean", BigDecimal::class.java),
                max = row.get("max", BigDecimal::class.java),
                value = row.get("value", BigDecimal::class.java),
                other = other
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    protected fun convertJdbcRow(rs: ResultSet): TimeSeriesMeter {
        val type = rs.getString("type")
        val timestamp = rs.getTimestamp("timestamp").toInstant()
        val campaign = rs.getString("campaign")
        val scenario = rs.getString("scenario")
        val tags = rs.getString("tags")
            ?.let { objectMapper.readValue(it, Map::class.java) } as Map<String, String>?
        val count = rs.getBigDecimal("count")
        val other = (rs.getString("other")
            ?.let { objectMapper.readValue(it, Map::class.java) } as Map<String, String>?)
            ?.mapValues { BigDecimal(it.value) }

        return if (type == MeterType.TIMER.value.lowercase()) {
            TimeSeriesMeter(
                name = rs.getString("name")!!,
                type = type,
                timestamp = timestamp,
                campaign = campaign,
                scenario = scenario,
                tags = tags,
                count = count?.toLong(),
                sumDuration = rs.getBigDecimal("sum")?.toLong()?.times(1_000)?.let(Duration::ofNanos),
                maxDuration = rs.getBigDecimal("max")?.toLong()?.times(1_000)?.let(Duration::ofNanos),
                meanDuration = rs.getBigDecimal("mean")?.toLong()?.times(1_000)?.let(Duration::ofNanos),
                other = other
            )
        } else {
            TimeSeriesMeter(
                name = rs.getString("name")!!,
                type = type,
                timestamp = timestamp,
                campaign = campaign,
                scenario = scenario,
                tags = tags,
                count = count?.toLong(),
                sum = rs.getBigDecimal("sum"),
                mean = rs.getBigDecimal("mean"),
                max = rs.getBigDecimal("max"),
                value = rs.getBigDecimal("value"),
                other = other
            )
        }
    }

    override suspend fun executeAggregations(
        preparedQueries: Map<String, String>,
        context: AggregationQueryExecutionContext
    ): Map<String, TimeSeriesValues> = emptyMap()

    override suspend fun retrieveRecords(
        preparedQueries: Map<String, String>,
        context: DataRetrievalQueryExecutionContext
    ): Map<String, Page<TimeSeriesRecord>> = emptyMap()

    override suspend fun retrieveUsedStorage(tenant: String): Long = 0L
}
