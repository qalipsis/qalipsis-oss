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
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.reactor.awaitSingleOrNull
import reactor.core.publisher.Flux

@Singleton
@Secondary
@Requirements(
    Requires(notEnv = [TRANSIENT]),
    Requires(property = "${EXPORT_CONFIGURATION}.default.enabled", notEquals = "false")
)
internal class PgsqlCampaignTimeSeriesDataProvider(
    @Named("default") private val connectionFactory: ConnectionFactory,
    objectMapper: ObjectMapper
) : AbstractCampaignTimeSeriesDataProvider(objectMapper) {

    override suspend fun retrieveCampaignMeters(
        tenant: String,
        campaignKeys: Collection<String>,
        scenarioNames: Collection<String>
    ): List<TimeSeriesMeter> {
        val params = mutableMapOf<String, Any>()
        params["\$1"] = tenant
        params["\$2"] = campaignKeys.toTypedArray()

        val sql = StringBuilder(
            "SELECT name, timestamp, \"type\", campaign, scenario, tags, \"count\", \"sum\", mean, \"max\", \"value\", other" +
                    " FROM campaign_meters WHERE tenant = \$1 AND campaign = any(array[\$2])"
        )

        if (scenarioNames.isNotEmpty()) {
            params["\$3"] = scenarioNames.toTypedArray()
            sql.append(" AND scenario = any (array[\$3])")
        }
        sql.append(" ORDER BY name, timestamp")

        val query = sql.toString()

        return Flux.usingWhen(
            connectionFactory.create(),
            { connection ->
                Flux.from(
                    connection.createStatement(query)
                        .also { stmt ->
                            params.forEach { (binding, value) -> stmt.bind(binding, value) }
                        }.execute()
                ).flatMap { result ->
                    result.map { row, metadata -> convertR2dbcRow(row, metadata) }
                }
            },
            Connection::close
        ).collectList().awaitSingleOrNull().orEmpty()
    }
}
