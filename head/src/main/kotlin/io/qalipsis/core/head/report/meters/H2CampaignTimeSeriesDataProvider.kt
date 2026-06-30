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
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Secondary
import io.qalipsis.api.config.MetersConfig.EXPORT_CONFIGURATION
import io.qalipsis.api.report.TimeSeriesMeter
import io.qalipsis.core.configuration.ExecutionEnvironments.TRANSIENT
import jakarta.inject.Singleton
import org.h2.jdbcx.JdbcDataSource
import javax.sql.DataSource

@Singleton
@Secondary
@Requirements(
    Requires(env = [TRANSIENT]),
    Requires(property = "${EXPORT_CONFIGURATION}.default.enabled", notEquals = "false")
)
internal class H2CampaignTimeSeriesDataProvider(
    objectMapper: ObjectMapper
) : AbstractCampaignTimeSeriesDataProvider(objectMapper) {

    private val dataSource: DataSource = JdbcDataSource().also {
        it.setURL("jdbc:h2:mem:qalipsis_campaign_meters;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
    }

    override suspend fun retrieveCampaignMeters(
        tenant: String,
        campaignKeys: Collection<String>,
        scenarioNames: Collection<String>
    ): List<TimeSeriesMeter> {
        return if (campaignKeys.isNotEmpty()) {
            val campaignPlaceholders = campaignKeys.joinToString(",") { "?" }
            val sql = buildString {
                append("SELECT name, timestamp, \"type\", campaign, scenario, tags, \"count\", \"sum\", mean, \"max\", \"value\", other")
                append(" FROM campaign_meters WHERE tenant = ? AND campaign IN ($campaignPlaceholders)")
                if (scenarioNames.isNotEmpty()) {
                    append(" AND scenario IN (${scenarioNames.joinToString(",") { "?" }})")
                }
                append(" ORDER BY name, timestamp")
            }
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    var idx = 1
                    stmt.setString(idx++, tenant)
                    campaignKeys.forEach { stmt.setString(idx++, it) }
                    scenarioNames.forEach { stmt.setString(idx++, it) }
                    stmt.executeQuery().use { rs ->
                        val result = mutableListOf<TimeSeriesMeter>()
                        while (rs.next()) {
                            result.add(convertJdbcRow(rs))
                        }
                        result
                    }
                }
            }
        } else {
            emptyList()
        }
    }
}
