package io.qalipsis.core.head.web

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.validation.Validated
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.campaign.ScenarioConfiguration
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.core.head.campaign.CampaignManager
import io.qalipsis.core.head.jdbc.repository.ScenarioRepository
import io.qalipsis.core.head.web.entity.CampaignConfigurationConverter
import io.qalipsis.core.head.web.entity.CampaignRequest
import io.qalipsis.core.head.web.entity.ScenarioRequest
import io.qalipsis.core.head.web.requestAnnotation.Tenant
import javax.validation.Valid

@Validated
@Controller("/campaigns")
internal class CampaignController(
    private val campaignManager: CampaignManager,
    private val scenarioRepository: ScenarioRepository
) {

    @Post
    suspend fun execute(@Tenant tenant: String, @Body @Valid campaign: CampaignRequest): HttpResponse<Unit> {
        campaignManager.start("", CampaignConfigurationConverter().convertCampaignRequestToConfiguration(tenant, campaign))
        return HttpResponse.accepted()
    }

    @Post("/validate")
    suspend fun validate(@Tenant tenant: String, @Body @Valid campaign: CampaignRequest): HttpResponse<String> {
        if (scenarioRepository.findActiveByName(tenant, campaign.scenarios.keys).size == campaign.scenarios.keys.size) {
            return HttpResponse.accepted()
        } else {
            return HttpResponse.badRequest("Scenarios with names ${campaign.scenarios.keys} are not exist")
        }
    }
}