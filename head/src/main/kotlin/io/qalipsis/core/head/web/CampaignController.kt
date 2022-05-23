package io.qalipsis.core.head.web

import io.micronaut.context.annotation.Requires
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.validation.Validated
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignManager
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.factory.ClusterFactoryService
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.web.annotations.Tenant
import io.qalipsis.core.head.web.model.CampaignConfigurationConverter
import io.qalipsis.core.head.web.model.CampaignRequest
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
    private val campaignConfigurationConverter: CampaignConfigurationConverter
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
    suspend fun execute(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String, @Body @Valid campaign: CampaignRequest
    ): HttpResponse<Unit> {
        campaignManager.start(
            "",
            campaignConfigurationConverter.convertCampaignRequestToConfiguration(tenant, campaign)
        )
        return HttpResponse.accepted()
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
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Missing rights to execute the operation"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
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
            HttpResponse.accepted()
        } else {
            HttpResponse.badRequest("Scenarios with names ${campaign.scenarios.keys} are not exist")
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
    suspend fun list(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(
            description = "It is  a comma-separated list of values to apply as wilcard filters on the the campaign, user and scenario campaigns",
            required = false,
            `in` = ParameterIn.QUERY
        ) @Nullable @QueryValue filter: String,
        @Parameter(
            description = "Field of the campaign to use in order to sort the results",
            required = false,
            `in` = ParameterIn.QUERY
        ) @Nullable @QueryValue sort: String
    ): HttpResponse<List<CampaignEntity>>? {
        return HttpResponse.ok(campaignService.getAllCampaigns(tenant, filter, sort))
    }
}