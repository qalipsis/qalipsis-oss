package io.qalipsis.core.head.web

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.validation.Validated
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignManager
import io.qalipsis.core.head.web.entity.CampaignRequest
import io.qalipsis.core.head.web.requestAnnotation.Tenant
import javax.validation.Valid

@Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
@Validated
@Controller("/campaigns")
internal class CampaignController(
    private val campaignManager: CampaignManager
) {

    @Post
    suspend fun execute(
        @Tenant tenant: String, @Body @Valid campaign: CampaignRequest
    ): HttpResponse<Void> {
        campaignManager.start(campaign.username, convertCampaignRequestToConfiguration(tenant, campaign))
        return HttpResponse.ok()
    }

    private fun convertCampaignRequestToConfiguration(
        tenant: String,
        campaign: CampaignRequest
    ): CampaignConfiguration {
        return CampaignConfiguration(
            tenant = tenant,
            name = campaign.name,
            speedFactor = campaign.speedFactor,
            startOffsetMs = campaign.startOffsetMs,
            scenarios = campaign.scenarios
        )
    }
}