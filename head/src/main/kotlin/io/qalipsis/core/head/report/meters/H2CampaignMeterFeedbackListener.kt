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

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.config.MetersConfig.EXPORT_CONFIGURATION
import io.qalipsis.core.configuration.ExecutionEnvironments.TRANSIENT
import jakarta.inject.Singleton
import org.h2.jdbcx.JdbcDataSource
import javax.sql.DataSource

@Singleton
@Requirements(
    Requires(env = [TRANSIENT]),
    Requires(property = "${EXPORT_CONFIGURATION}.default.enabled", notEquals = "false")
)
internal class H2CampaignMeterFeedbackListener : AbstractCampaignMeterFeedbackListener() {

    override val dataSource: DataSource = JdbcDataSource().also {
        it.setURL("jdbc:h2:mem:qalipsis_campaign_meters;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
        it.connection.use { conn ->
            conn.createStatement().use { stmt -> stmt.execute(CREATE_TABLE_SQL) }
        }
    }

    private companion object {
        const val CREATE_TABLE_SQL = """CREATE TABLE IF NOT EXISTS campaign_meters (
            name VARCHAR(255) NOT NULL,
            tags VARCHAR(4096),
            timestamp TIMESTAMP NOT NULL,
            tenant VARCHAR(255),
            campaign VARCHAR(255),
            scenario VARCHAR(255),
            "type" VARCHAR(50) NOT NULL,
            "count" DOUBLE PRECISION,
            "value" DOUBLE PRECISION,
            "sum" DOUBLE PRECISION,
            mean DOUBLE PRECISION,
            unit VARCHAR(50),
            "max" DOUBLE PRECISION,
            other VARCHAR(4096)
        )"""
    }
}
