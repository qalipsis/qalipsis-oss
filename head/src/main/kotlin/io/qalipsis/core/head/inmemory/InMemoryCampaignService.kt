package io.qalipsis.core.head.inmemory

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.query.Page
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.Scenario
import jakarta.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(env = [ExecutionEnvironments.TRANSIENT])
)
internal class InMemoryCampaignService : CampaignService {

    private var currentCampaign: Campaign? = null

    private val updateLock = Mutex(false)

    override suspend fun create(
        configurer: String,
        campaignDisplayName: String,
        campaignConfiguration: CampaignConfiguration
    ): Campaign {
        return updateLock.withLock {
            currentCampaign = Campaign(
                version = Instant.now(),
                key = campaignConfiguration.key,
                name = campaignDisplayName,
                speedFactor = campaignConfiguration.speedFactor,
                start = null,
                end = null,
                result = null,
                configurerName = null,
                scenarios = campaignConfiguration.scenarios.map {
                    Scenario(
                        Instant.now(),
                        it.key,
                        it.value.minionsCount
                    )
                }
            )
            currentCampaign!!
        }
    }

    override suspend fun open(tenant: String, campaignKey: CampaignKey) {
        updateLock.withLock {
            currentCampaign = currentCampaign?.copy(start = Instant.now())
        }
    }

    override suspend fun openScenario(tenant: String, campaignKey: CampaignKey, scenarioName: ScenarioName) = Unit

    override suspend fun closeScenario(tenant: String, campaignKey: CampaignKey, scenarioName: ScenarioName) = Unit

    override suspend fun close(tenant: String, campaignKey: String, result: ExecutionStatus): Campaign {
        return updateLock.withLock {
            currentCampaign = currentCampaign!!.copy(end = Instant.now())
            currentCampaign!!
        }
    }

    override suspend fun search(
        tenant: String, filters: Collection<String>, sort: String?, page: Int, size: Int
    ): Page<Campaign> {
        // Nothing to do.
        return Page(0, 0, 0, emptyList())
    }

    override suspend fun abort(tenant: String, aborter: String, campaignKey: String) {
        currentCampaign = currentCampaign!!.copy(end = Instant.now(), result = ExecutionStatus.ABORTED)
    }
}