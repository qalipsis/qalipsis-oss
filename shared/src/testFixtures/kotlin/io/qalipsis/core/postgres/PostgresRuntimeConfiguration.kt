/*
 * QALIPSIS
 * Copyright (C) 2024 AERIS IT Solutions GmbH
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

package io.qalipsis.core.postgres

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.time.Duration
import kotlin.math.pow

object PostgresRuntimeConfiguration {

    /**
     * Default image name and tag.
     */
    private const val DEFAULT_DOCKER_IMAGE = "postgres:14.1-alpine"

    /**
     * Default db name.
     */
    private const val DB_NAME = "qalipsis"

    /**
     * Default username.
     */
    private const val USERNAME = "qalipsis_user"

    /**
     * Default password.
     */
    private const val PASSWORD = "qalipsis-pwd"

    fun PostgreSQLContainer<*>.testProperties(): Map<String, String> {
        return mapOf(
            "datasource.host" to "localhost",
            "datasource.port" to "$firstMappedPort",
            "datasource.database" to DB_NAME,
            "datasource.username" to USERNAME,
            "datasource.password" to PASSWORD,
            "r2dbc.datasources.default.options.initialSize" to "1",
            "r2dbc.datasources.default.options.maxSize" to "4",
            "logging.level.io.micronaut.data.query" to "TRACE"
        )
    }

    fun createContainer(imageNameAndTag: String = DEFAULT_DOCKER_IMAGE): PostgreSQLContainer<*> {
        return PostgreSQLContainer<Nothing>(imageNameAndTag)
            .apply {
                withStartupAttempts(5)
                withCreateContainerCmdModifier { cmd ->
                    cmd.hostConfig!!.withMemory(50 * 1024.0.pow(2).toLong()).withCpuCount(2)
                }
                waitingFor(Wait.forListeningPort())
                withStartupTimeout(Duration.ofSeconds(60))

                withDatabaseName(DB_NAME)
                withUsername(USERNAME)
                withPassword(PASSWORD)
                withInitScript("sql/pgsql-init.sql")
            }
    }


}
