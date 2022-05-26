package io.qalipsis.core.head.inmemory

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.data.model.Page
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.context.CampaignName
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import jakarta.inject.Singleton

@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(env = [ExecutionEnvironments.VOLATILE])
)
internal class InMemoryCampaignService : CampaignService {

    override suspend fun create(configurer: String, campaignConfiguration: CampaignConfiguration) {
        // Nothing to do.
    }

    override suspend fun close(campaignName: CampaignName, result: ExecutionStatus) {
        // Nothing to do.
    }

    override suspend fun getAllCampaigns(
        tenant: String, filter: String?, sort: String?, page: Int, size: Int
    ): Page<CampaignEntity> {
        // Nothing to do.
        return Page.empty()
    }
}