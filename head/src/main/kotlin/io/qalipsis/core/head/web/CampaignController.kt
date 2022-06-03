package io.qalipsis.core.head.web

import io.micronaut.context.annotation.Requires
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.validation.Validated
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignManager
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignRequest
import io.qalipsis.core.head.model.converter.CampaignConverter
import io.qalipsis.core.head.security.Permissions
import io.qalipsis.core.head.web.annotations.Tenant
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
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
            ApiResponse(responseCode = "200", description = "Details of the successfully created user"),
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
            ApiResponse(responseCode = "200", description = "Details of the successfully created user"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
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
}