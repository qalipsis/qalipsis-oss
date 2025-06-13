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
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.annotation.Status
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.validation.Validated
import io.qalipsis.api.query.Page
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.cluster.security.Permissions
import io.qalipsis.cluster.security.Tenant
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignExecutor
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.campaign.scheduler.CampaignScheduler
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.CampaignExecutionDetails
import io.qalipsis.core.head.report.CampaignReportProvider
import io.qalipsis.core.head.web.ControllerUtils.asFilters
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.annotation.Nullable
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.Positive
import javax.validation.constraints.PositiveOrZero


/**
 * Controller for REST calls related to campaign operations.
 *
 * @author Palina Bril
 */
@Validated
@Controller("/campaigns")
@Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
@Version("1.0")
internal class CampaignController(
    private val campaignExecutor: CampaignExecutor,
    private val campaignService: CampaignService,
    private val clusterFactoryService: FactoryService,
    private val campaignReportProvider: CampaignReportProvider,
    private val campaignScheduler: CampaignScheduler,
) {

    /**
     * REST endpoint to start campaign.
     */
    @Post
    @Operation(
        summary = "Start a new campaign",
        description = "Start a new campaign with the provided details of campaign configuration and attach it to the contextual tenant",
        responses = [
            ApiResponse(responseCode = "200", description = "Campaign started successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Missing rights to execute the operation"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(Permissions.WRITE_CAMPAIGN)
    @Timed("campaigns-execute")
    suspend fun execute(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(hidden = true) authentication: Authentication,
        @Body @Valid campaign: CampaignConfiguration,
    ): Campaign {
        val campaignKey = campaignExecutor.start(tenant, authentication.name, campaign).key
        return campaignService.retrieve(tenant, campaignKey)
    }

    /**
     * REST endpoint to validate the campaign configuration.
     */
    @Post("/validate")
    @Operation(
        summary = "Validate a campaign configuration",
        description = "Validate a campaign configuration with the contextual tenant",
        responses = [
            ApiResponse(responseCode = "200", description = "Campaign validated successfully"),
            ApiResponse(responseCode = "400", description = "Invalid campaign configuration provide"),
            ApiResponse(responseCode = "401", description = "Missing rights to execute the operation"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(Permissions.WRITE_CAMPAIGN)
    @Timed("campaigns-validate")
    @Status(HttpStatus.NO_CONTENT)
    suspend fun validate(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Body @Valid campaign: CampaignConfiguration,
    ) {
        require(
            clusterFactoryService.getActiveScenarios(
                tenant,
                campaign.scenarios.keys
            ).size == campaign.scenarios.keys.size
        ) { "Scenarios with names ${campaign.scenarios.keys.joinToString()} are unknown or currently disabled" }
    }

    @Get
    @Operation(
        summary = "Search campaigns",
        description = "Search the past and currently running campaigns, with the scenarios they executed and their status",
        responses = [
            ApiResponse(responseCode = "200", description = "Details of the successfully listed campaigns"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Missing rights to execute the operation"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(Permissions.READ_CAMPAIGN)
    @Timed("campaigns-search")
    suspend fun search(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(
            description = "Comma-separated list of values to apply as wildcard filters on the campaign, user and scenarios names",
            required = false,
            `in` = ParameterIn.QUERY
        ) @Nullable @QueryValue("filter", defaultValue = "") filter: String,
        @Parameter(
            description = "Field of the campaign to use in order to sort the results",
            required = false,
            `in` = ParameterIn.QUERY
        ) @Nullable @QueryValue(defaultValue = "start:desc") sort: String,
        @Parameter(
            description = "0-based number of the page to retrieve",
            required = false,
            `in` = ParameterIn.QUERY
        ) @Nullable @QueryValue(defaultValue = "0") @PositiveOrZero page: String,
        @Parameter(
            description = "Size of the page to retrieve",
            required = false,
            `in` = ParameterIn.QUERY
        ) @Nullable @QueryValue(defaultValue = "20") @Positive size: String,
        @Parameter(
            description = "List of excluded campaign status",
            required = false,
            `in` = ParameterIn.QUERY
        ) @Nullable @QueryValue(defaultValue = "") excludedStatuses: Collection<ExecutionStatus>,
    ): Page<Campaign> {
        return campaignService.search(tenant, filter.asFilters(), sort, page.toInt(), size.toInt(), excludedStatuses)
    }

    /**
     * REST endpoint to abort campaign.
     */
    @Post("/{campaignKey}/abort")
    @Operation(
        summary = "Abort a campaign",
        description = "Abort a campaign with the provided campaign name and details of abortion",
        responses = [
            ApiResponse(responseCode = "200", description = "Campaign aborted successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Missing rights to execute the operation"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Timed("campaigns-abort")
    @Status(HttpStatus.ACCEPTED)
    suspend fun abort(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(
            description = "Key of the campaign to abort",
            required = true,
            `in` = ParameterIn.PATH
        ) @NotBlank @PathVariable campaignKey: String,
        @Parameter(
            description = "Force the campaign to fail when set to true, defaults to false",
            required = false,
            `in` = ParameterIn.QUERY
        ) @Nullable @QueryValue(defaultValue = "false") hard: Boolean,
    ) {
        campaignExecutor.abort(tenant, authentication.name, campaignKey, hard)
    }

    /**
     * REST endpoint to get the complete execution report of a completed or running set of campaigns.
     */
    @Get("/{campaignKeys}")
    @Operation(
        summary = "Retrieve a list of campaigns reports",
        description = "Reports the details of the execution of a completed or running campaigns and their scenarios",
        responses = [
            ApiResponse(responseCode = "200", description = "Details of the successfully retrieved campaigns reports"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Missing rights to execute the operation"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(Permissions.READ_CAMPAIGN)
    @Timed("campaigns-retrieve")
    suspend fun retrieve(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(
            description = "Comma separated list of keys of the campaigns to retrieve",
            required = true,
            `in` = ParameterIn.PATH
        ) @NotEmpty @PathVariable campaignKeys: List<String>,
    ): Collection<CampaignExecutionDetails> {
        return campaignReportProvider.retrieveCampaignsReports(tenant, campaignKeys)
    }

    /**
     * REST endpoint to get the complete execution report of a completed campaign.
     */
    @Get("/{campaignKey}/configuration")
    @Operation(
        summary = "Retrieve the initial configuration of a campaign",
        description = "Returns the configuration received from the client when creating a new campaign",
        responses = [
            ApiResponse(responseCode = "200", description = "Details of the successfully retrieved campaign report"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Missing rights to execute the operation"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(Permissions.READ_CAMPAIGN)
    @Timed("campaigns-configuration-retrieve")
    suspend fun retrieveConfiguration(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(
            description = "Key of the campaign to retrieve",
            required = true,
            `in` = ParameterIn.PATH
        ) @NotBlank @PathVariable campaignKey: String,
    ): CampaignConfiguration {
        return campaignService.retrieveConfiguration(tenant, campaignKey)
    }

    /**
     * REST endpoint to replay the campaign.
     */
    @Post("/{campaignKey}/replay")
    @Operation(
        summary = "Replay the campaign",
        description = "Replay campaign with the provided campaign key",
        responses = [
            ApiResponse(responseCode = "200", description = "Campaign replayed successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Missing rights to execute the operation"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(Permissions.WRITE_CAMPAIGN)
    @Timed("campaigns-replay")
    suspend fun replay(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(
            description = "Key of the campaign to replay",
            required = true,
            `in` = ParameterIn.PATH
        ) @NotBlank @PathVariable campaignKey: String,
        @Parameter(hidden = true) authentication: Authentication,
    ): Campaign {
        val newCampaignKey = campaignExecutor.replay(tenant, authentication.name, campaignKey).key
        return campaignService.retrieve(tenant, newCampaignKey)
    }

    /**
     * REST endpoint to schedule a campaign test.
     */
    @Post("schedule")
    @Operation(
        summary = "Schedule a campaign test",
        description = "Schedule a campaign with the provided details of campaign configuration and attach it to the contextual tenant",
        responses = [
            ApiResponse(responseCode = "200", description = "Campaign test has been scheduled successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Missing rights to execute the operation"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(Permissions.WRITE_CAMPAIGN)
    @Timed("campaigns-schedule")
    suspend fun schedule(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(hidden = true) authentication: Authentication,
        @Body @Valid configuration: CampaignConfiguration,
    ): Campaign {
        val campaignKey = campaignScheduler.schedule(tenant, authentication.name, configuration).key
        return campaignService.retrieve(tenant, campaignKey)
    }

    /**
     * REST endpoint to update a scheduled campaign.
     */
    @Put("schedule/{campaignKey}")
    @Operation(
        summary = "Updates a scheduled campaign test",
        description = "Updates a scheduled campaign with the newly provided details of campaign configuration",
        responses = [
            ApiResponse(responseCode = "200", description = "Scheduled campaign test has been updated successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Missing rights to execute the operation"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(Permissions.WRITE_CAMPAIGN)
    @Timed("campaigns-schedule-update")
    suspend fun reschedule(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(hidden = true) authentication: Authentication,
        @NotBlank @PathVariable campaignKey: String,
        @Body @Valid configuration: CampaignConfiguration,
    ): Campaign {
        val updatedCampaignKey = campaignScheduler.update(tenant, authentication.name, campaignKey, configuration).key
        return campaignService.retrieve(tenant, updatedCampaignKey)
    }
}