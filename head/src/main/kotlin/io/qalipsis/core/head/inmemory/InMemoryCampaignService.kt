package io.qalipsis.core.head.inmemory

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignConfiguration
import io.qalipsis.core.head.campaign.CampaignService
import jakarta.inject.Singleton

@Singleton
@Requires(env = [ExecutionEnvironments.VOLATILE])
internal class InMemoryCampaignService : CampaignService {

    override suspend fun save(campaignConfiguration: CampaignConfiguration) {
        // Nothing to do.
    }

    override suspend fun close(campaignId: CampaignId, result: ExecutionStatus) {
        // Nothing to do.
    }


}