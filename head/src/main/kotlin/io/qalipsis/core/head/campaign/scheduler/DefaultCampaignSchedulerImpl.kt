package io.qalipsis.core.head.campaign.scheduler

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors.ORCHESTRATION_EXECUTOR_NAME
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.lang.tryAndLog
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignExecutor
import io.qalipsis.core.head.campaign.CampaignPreparator
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.security.TenantProvider
import io.qalipsis.core.head.security.UserProvider
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * Default implementation for [CampaignScheduler] that uses coroutine-based delays for scheduling.
 *
 * @author Joël Valère
 */
@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.SINGLE_HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
)
class DefaultCampaignSchedulerImpl(
    userProvider: UserProvider,
    campaignExecutor: CampaignExecutor,
    tenantProvider: TenantProvider,
    campaignRepository: CampaignRepository,
    factoryService: FactoryService,
    campaignPreparator: CampaignPreparator,
    private val scheduledCampaignsRegistry: ScheduledCampaignsRegistry,
    @Named(ORCHESTRATION_EXECUTOR_NAME) coroutineScope: CoroutineScope
) : AbstractCampaignScheduler(
    userProvider, campaignExecutor, tenantProvider, campaignRepository,
    factoryService, campaignPreparator, coroutineScope
) {

    override suspend fun schedule(campaignKey: CampaignKey, instant: Instant) {
        val scheduleJob = coroutineScope.launch {
            tryAndLog(log) {
                delay(Duration.between(Instant.now(), instant).toMillis().coerceAtLeast(0))
                executeCampaign(campaignKey)
            }
        }
        scheduledCampaignsRegistry.updateSchedule(campaignKey, scheduleJob)
    }

    override suspend fun cancelSchedule(campaignKey: CampaignKey) {
        scheduledCampaignsRegistry.cancelSchedule(campaignKey)
    }

    companion object {

        private val log = logger()

    }
}
