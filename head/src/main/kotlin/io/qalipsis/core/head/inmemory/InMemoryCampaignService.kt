package io.qalipsis.core.head.inmemory

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.query.Page
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioConfiguration
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignConfiguration
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
internal class InMemoryCampaignService(
    private val idGenerator: IdGenerator
) : CampaignService {

    private var currentCampaign: Campaign? = null

    private val updateLock = Mutex(false)

    override suspend fun create(
        tenant: String,
        configurer: String,
        campaignConfiguration: CampaignConfiguration
    ): RunningCampaign {
        val runningCampaign = RunningCampaign(
            tenant = tenant,
            key = idGenerator.short(),
            speedFactor = campaignConfiguration.speedFactor,
            startOffsetMs = campaignConfiguration.startOffsetMs,
            hardTimeout = campaignConfiguration.hardTimeout ?: false,
            scenarios = campaignConfiguration.scenarios.mapValues { ScenarioConfiguration(it.value.minionsCount) }
        )

        updateLock.withLock {
            currentCampaign = Campaign(
                version = Instant.now(),
                key = runningCampaign.key,
                name = campaignConfiguration.name,
                speedFactor = campaignConfiguration.speedFactor,
                scheduledMinions = campaignConfiguration.scenarios.values.sumOf { it.minionsCount },
                hardTimeout = campaignConfiguration.hardTimeout,
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

        return runningCampaign
    }

    override suspend fun retrieve(tenant: String, campaignKey: CampaignKey): Campaign {
        return currentCampaign!!
    }

    override suspend fun start(tenant: String, campaignKey: CampaignKey, start: Instant, timeout: Instant?) {
        updateLock.withLock {
            currentCampaign = currentCampaign?.copy(start = Instant.now(), timeout = timeout)
        }
    }

    override suspend fun startScenario(
        tenant: String,
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        start: Instant
    ) = Unit

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