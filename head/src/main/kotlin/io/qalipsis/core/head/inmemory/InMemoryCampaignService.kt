package io.qalipsis.core.head.inmemory

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.query.Page
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.Scenario
import jakarta.inject.Singleton
import java.time.Instant

@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(env = [ExecutionEnvironments.TRANSIENT])
)
internal class InMemoryCampaignService : CampaignService {

    override suspend fun create(
        configurer: String,
        campaignDisplayName: String,
        campaignConfiguration: CampaignConfiguration
    ): Campaign {
        // Nothing to do.
        return Campaign(
            version = Instant.now(),
            key = campaignConfiguration.key,
            name = campaignDisplayName,
            speedFactor = campaignConfiguration.speedFactor,
            start = Instant.now(),
            end = null,
            result = null,
            configurerName = null,
            scenarios = campaignConfiguration.scenarios.map { Scenario(Instant.now(), it.key, it.value.minionsCount) }
        )
    }

    override suspend fun close(tenant: String, campaignKey: String, result: ExecutionStatus): Campaign {
        // Nothing to do.
        return Campaign(
            version = Instant.now(),
            key = campaignKey,
            name = campaignKey,
            speedFactor = 1.0,
            start = Instant.now(),
            end = Instant.now(),
            result = result,
            configurerName = null,
            emptyList()
        )
    }

    override suspend fun search(
        tenant: String, filters: Collection<String>, sort: String?, page: Int, size: Int
    ): Page<Campaign> {
        // Nothing to do.
        return Page(0, 0, 0, emptyList())
    }

    override suspend fun setAborter(tenant: String, aborter: String, campaignKey: String) {
        // Nothing to do.
    }
}