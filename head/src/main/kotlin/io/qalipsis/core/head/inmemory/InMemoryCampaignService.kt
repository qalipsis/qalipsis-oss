package io.qalipsis.core.head.inmemory

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.context.CampaignName
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignService
import jakarta.inject.Singleton

@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(env = [ExecutionEnvironments.VOLATILE])
)
internal class InMemoryCampaignService : CampaignService {

    override suspend fun save(configurer: String, campaignConfiguration: CampaignConfiguration) {
        // Nothing to do.
    }

    override suspend fun close(campaignName: CampaignName, result: ExecutionStatus) {
        // Nothing to do.
    }


}