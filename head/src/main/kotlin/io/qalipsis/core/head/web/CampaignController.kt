package io.qalipsis.core.head.web

import io.micronaut.context.annotation.Requires
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.validation.Validated
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignManager
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignRequest
import io.qalipsis.core.head.model.Page
import io.qalipsis.core.head.model.converter.CampaignConverter
import io.qalipsis.core.head.security.Permissions
import io.qalipsis.core.head.web.annotations.Tenant
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import javax.annotation.Nullable
import javax.validation.Valid
import javax.validation.constraints.NotBlank


/**
 * Controller for REST calls related to campaign operations.
 *
 * @author Palina Bril
 */
@Validated
@Controller("/campaigns")
@Requires(env = [ExecutionEnvironments.HEAD])
@Version("1.0")
internal class CampaignController(
    private val campaignManager: CampaignManager,
    private val campaignService: CampaignService,
    private val clusterFactoryService: FactoryService,
    private val campaignConverter: CampaignConverter
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
    @Secured(Permissions.CREATE_CAMPAIGN)
    suspend fun execute(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(hidden = true) authentication: Authentication,
        @Body @Valid campaign: CampaignRequest
    ): HttpResponse<Campaign> {
        return HttpResponse.ok(
            campaignManager.start(
                authentication.name,
                campaign.name,
                campaignConverter.convertRequest(tenant, campaign)
            )
        )
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
    @Secured(Permissions.CREATE_CAMPAIGN)
    suspend fun validate(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String, @Body @Valid campaign: CampaignRequest
    ): HttpResponse<String> {
        return if (clusterFactoryService.getActiveScenarios(
                tenant,
                campaign.scenarios.keys
            ).size == campaign.scenarios.keys.size
        ) {
            HttpResponse.noContent()
        } else {
            HttpResponse.badRequest("Scenarios with names ${campaign.scenarios.keys.joinToString()} are unknown or currently disabled")
        }
    }

    @Get
    @Operation(
        summary = "List campaigns",
        description = "List the past and currently running campaigns, the scenarios they executed and their status",
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
    suspend fun list(
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
        ) @Nullable @QueryValue filter: String,
        @Parameter(
            description = "Field of the campaign to use in order to sort the results",
            required = false,
            `in` = ParameterIn.QUERY
        ) @Nullable @QueryValue sort: String,
        @Parameter(
            description = "Field of the campaign to use in order to sort the results",
            required = false,
            `in` = ParameterIn.QUERY
        ) @Nullable @QueryValue(defaultValue = 0.toString()) page: String,
        @Parameter(
            description = "Field of the campaign to use in order to sort the results",
            required = false,
            `in` = ParameterIn.QUERY
        ) @Nullable @QueryValue(defaultValue = "20") size: String
    ): HttpResponse<Page<Campaign>> {
        return HttpResponse.ok(campaignService.search(tenant, filter, sort, page.toInt(), size.toInt()))
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
    suspend fun abort(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(
            description = "Campaign name of the campaign to abort",
            required = true,
            `in` = ParameterIn.PATH
        ) @NotBlank @PathVariable campaignKey: String,
        @Parameter(
            description = "Force the campaign to fail when set to true, defaults to false",
            required = false,
            `in` = ParameterIn.QUERY
        ) @Nullable @QueryValue(defaultValue = "false") hard: String
    ): HttpResponse<Unit> {
        campaignManager.abort(authentication.name, tenant, campaignKey, hard.toBoolean())
        return HttpResponse.accepted()
    }
}