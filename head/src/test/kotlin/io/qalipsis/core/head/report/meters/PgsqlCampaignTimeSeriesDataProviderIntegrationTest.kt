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

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.transaction.jdbc.DelegatingDataSource
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.postgres.PostgresRuntimeConfiguration
import io.qalipsis.core.postgres.PostgresRuntimeConfiguration.testProperties
import jakarta.inject.Inject
import jakarta.inject.Named
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import javax.sql.DataSource

@Testcontainers
@MicronautTest(environments = [ExecutionEnvironments.POSTGRESQL], startApplication = false, transactional = false)
internal class PgsqlCampaignTimeSeriesDataProviderIntegrationTest :
    AbstractCampaignTimeSeriesDataProviderIntegrationTest(), TestPropertyProvider {

    @Inject
    @field:Named("default")
    private lateinit var jdbcDataSource: DataSource

    override fun getProperties(): Map<String, String> = pgsqlContainer.testProperties() + mapOf(
        "datasources.default.url" to pgsqlContainer.jdbcUrl,
        "datasources.default.username" to pgsqlContainer.username,
        "datasources.default.password" to pgsqlContainer.password,
        "datasources.default.driverClassName" to "org.postgresql.Driver",
        "datasources.default.schema" to "qalipsis",
        "datasource.schema" to "qalipsis"
    )

    override suspend fun setUpTest() {
        DelegatingDataSource.unwrapDataSource(jdbcDataSource).connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """CREATE TABLE IF NOT EXISTS qalipsis.campaign_meters (
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
                )
            }
            conn.createStatement().use { it.execute("TRUNCATE TABLE qalipsis.campaign_meters") }
        }
    }

    companion object {
        @Container
        @JvmStatic
        val pgsqlContainer: PostgreSQLContainer<*> = PostgresRuntimeConfiguration.createContainer()
    }
}
