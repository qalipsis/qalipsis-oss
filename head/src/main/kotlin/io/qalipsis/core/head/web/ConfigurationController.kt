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
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.validation.Validated
import io.qalipsis.cluster.security.Permissions
import io.qalipsis.cluster.security.SecurityConfiguration
import io.qalipsis.cluster.security.Tenant
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignConfigurationProvider
import io.qalipsis.core.head.configuration.DefaultCampaignConfiguration
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import javax.validation.constraints.NotBlank


/**
 * Controller for REST calls related to retrieving the configuration, for example, security configuration.
 *
 * @author Palina Bril
 */
@Validated
@Controller("/configuration")
@Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
@Version("1.0")
@Tag(name = "Configuration")
class ConfigurationController(
    private val securityConfiguration: SecurityConfiguration,
    private val campaignConfigurationProvider: CampaignConfigurationProvider,
) {

    /**
     * REST endpoint to get security configuration.
     */
    @Get("/security")
    @Operation(
        summary = "Provide the security configuration",
        description = "Provide the details of the strategy model to apply in the frontend and its configuration",
        responses = [
            ApiResponse(responseCode = "200", description = "Security configuration retrieved successfully")
        ]
    )
    @Secured(SecurityRule.IS_ANONYMOUS)
    @Timed("configuration-security")
    suspend fun retrieveSecurity(): SecurityConfiguration {
        return securityConfiguration
    }

    /**
     * REST endpoint to return the default and validation values to create a new campaign.
     */
    @Get("/campaign")
    @Operation(
        summary = "Returns the default and validation values to create a new campaign",
        description = "Provide the default and validation values to create a new campaign",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Default and validation values to create a new campaign retrieved successfully"
            ),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Missing rights to execute the operation"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(Permissions.WRITE_CAMPAIGN)
    @Timed("configuration-campaign")
    suspend fun campaign(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String
    ): DefaultCampaignConfiguration {
        return campaignConfigurationProvider.retrieveCampaignConfigurationDetails(tenant)
    }
}