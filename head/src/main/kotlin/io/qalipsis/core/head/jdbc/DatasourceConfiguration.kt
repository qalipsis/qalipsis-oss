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
package io.qalipsis.core.head.jdbc

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Positive

/**
 * Configuration of the JDBC data source, used to create the schema by liquibase.
 */
@ConfigurationProperties(value = "datasource")
@Requires(property = "liquibase.enabled", notEquals = "false")
class DatasourceConfiguration {

    @field:NotBlank
    var host: String = "localhost"

    @field:Positive
    var port: Int = 5432

    @field:NotBlank
    var database: String = "qalipsis"

    @field:NotBlank
    var schema: String = "qalipsis"

    @field:NotBlank
    var username: String = "qalipsis"

    @field:NotBlank
    var password: String = "qalipsis"

    var properties: Map<String, String> = emptyMap()
}
