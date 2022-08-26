package io.qalipsis.core.head.web

import io.micrometer.core.annotation.Timed
import io.micronaut.context.annotation.Requires
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.validation.Validated
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.configuration.DefaultCampaignConfiguration
import io.qalipsis.core.head.configuration.DefaultValuesCampaignConfiguration
import io.qalipsis.core.head.configuration.Stage
import io.qalipsis.core.head.configuration.Validation
import io.qalipsis.core.head.model.SecurityConfiguration
import io.qalipsis.core.head.security.Permissions
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement


/**
 * Controller for REST calls related to retrieving the configuration, for example, security configuration.
 *
 * @author Palina Bril
 */
@Validated
@Controller("/configuration")
@Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
@Version("1.0")
internal class ConfigurationController(
    private val securityConfiguration: SecurityConfiguration,
    private val defaultCampaignConfiguration: DefaultCampaignConfiguration
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
    suspend fun retrieveSecurity(): HttpResponse<SecurityConfiguration> {
        return HttpResponse.ok(securityConfiguration)
    }

    /**
     * REST endpoint to returns the default and validation values to create a new campaign.
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
    suspend fun campaign(): HttpResponse<DefaultValuesCampaignConfiguration> {
        return HttpResponse.ok(
            DefaultValuesCampaignConfiguration(
                validation = Validation(
                    maxMinionsCount = defaultCampaignConfiguration.validation.maxMinionsCount,
                    maxExecutionDuration = defaultCampaignConfiguration.validation.maxExecutionDuration,
                    maxScenariosCount = defaultCampaignConfiguration.validation.maxScenariosCount,
                    stage = Stage(
                        minMinionsCount = defaultCampaignConfiguration.validation.stage.minMinionsCount,
                        maxMinionsCount = defaultCampaignConfiguration.validation.stage.maxMinionsCount ?: defaultCampaignConfiguration.validation.maxMinionsCount,
                        minResolution = defaultCampaignConfiguration.validation.stage.minResolution,
                        maxResolution = defaultCampaignConfiguration.validation.stage.maxResolution,
                        minDuration = defaultCampaignConfiguration.validation.stage.minDuration,
                        maxDuration = defaultCampaignConfiguration.validation.stage.maxDuration ?: defaultCampaignConfiguration.validation.maxExecutionDuration,
                        minStartDuration = defaultCampaignConfiguration.validation.stage.minStartDuration,
                        maxStartDuration = defaultCampaignConfiguration.validation.stage.maxStartDuration
                    )
                )
            )
        )
    }
}