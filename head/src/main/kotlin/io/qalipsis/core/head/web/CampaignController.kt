package io.qalipsis.core.head.web

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.validation.Validated
import io.qalipsis.core.head.campaign.CampaignManager
import io.qalipsis.core.head.factory.ClusterFactoryService
import io.qalipsis.core.head.web.entity.CampaignConfigurationConverter
import io.qalipsis.core.head.web.entity.CampaignRequest
import io.qalipsis.core.head.web.requestAnnotation.Tenant
import java.security.PrivateKey
import javax.validation.Valid

/**
 * Controller for REST calls related to campaign operations.
 *
 * @author Palina Bril
 */
@Validated
@Controller("/campaigns")
internal class CampaignController(
    private val campaignManager: CampaignManager,
    private val clusterFactoryService: ClusterFactoryService,
    private val campaignConfigurationConverter: CampaignConfigurationConverter
) {

    /**
     * REST endpoint to start campaign.
     */
    @Post
    suspend fun execute(@Tenant tenant: String, @Body @Valid campaign: CampaignRequest): HttpResponse<Unit> {
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
    suspend fun validate(@Tenant tenant: String, @Body @Valid campaign: CampaignRequest): HttpResponse<String> {
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
}