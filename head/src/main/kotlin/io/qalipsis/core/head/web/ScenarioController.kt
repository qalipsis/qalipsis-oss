/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.core.head.web

import io.micrometer.core.annotation.Timed
import io.micronaut.context.annotation.Requires
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.micronaut.validation.Validated
import io.qalipsis.cluster.security.Permissions
import io.qalipsis.cluster.security.Tenant
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.factory.FactoryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.annotation.Nullable
import javax.validation.constraints.NotBlank

@Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
@Validated
@Controller("/scenarios")
@Version("1.0")
class ScenarioController(
    private val factoryService: FactoryService
) {

    @Get
    @Operation(
        summary = "Get a list of available scenarios",
        description = "Get a list of available scenarios of the tenant",
        responses = [
            ApiResponse(responseCode = "200", description = "Details of the available scenarios"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Missing rights to execute the operation"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(Permissions.READ_SCENARIO)
    @Timed("scenarios-search")
    suspend fun searchScenarios(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(
            description = "Field of the scenarios to use in order to sort the results, can be name, default_minions_count or enabled, add the postfix `:desc` to the field name to reverse the order",
            required = false,
            `in` = ParameterIn.QUERY
        ) @Nullable @QueryValue sort: String?
    ): List<ScenarioSummary> {
        return factoryService.getAllActiveScenarios(tenant, sort).toList()
    }
}