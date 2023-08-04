package io.qalipsis.core.head.campaign.scheduler

import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors.ORCHESTRATION_EXECUTOR_NAME
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.ExecutionStatus.SCHEDULED
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignExecutor
import io.qalipsis.core.head.campaign.CampaignPreparator
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.lifetime.HeadStartupComponent
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * Default implementation for [CampaignScheduler].
 *
 * @author Joël Valère
 */
@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
)
internal class DefaultCampaignSchedulerImpl(
    private val userRepository: UserRepository,
    private val campaignExecutor: CampaignExecutor,
    private val tenantRepository: TenantRepository,
    private val campaignRepository: CampaignRepository,
    private val factoryService: FactoryService,
    private val campaignPreparator: CampaignPreparator,
    private val scheduledCampaignsRegistry: ScheduledCampaignsRegistry,
    @Named(ORCHESTRATION_EXECUTOR_NAME) private val coroutineScope: CoroutineScope
) : CampaignScheduler, HeadStartupComponent {

    /**
     * Retrieve all the configurations of campaignEntities where the result is [SCHEDULED] and schedule these campaigns.
     */
    override fun init() {
        coroutineScope.launch {
            val campaigns = campaignRepository.findByResult(result = SCHEDULED)
            campaigns.forEach { campaign ->
                if (campaign.start?.isBefore(Instant.now()) == true) {
                    schedule(campaign.key, Instant.now())
                } else if (campaign.start?.isAfter(Instant.now()) == true) {
                    schedule(campaign.key, campaign.start)
                }
            }
        }
    }

    override suspend fun schedule(campaignKey: CampaignKey, instant: Instant) {
        val scheduleJob = coroutineScope.launch {
            schedulingExecution(campaignKey, instant)
        }
        scheduledCampaignsRegistry.updateSchedule(campaignKey, scheduleJob)
    }

    @LogInput
    override suspend fun schedule(
        tenant: String,
        configurer: String,
        configuration: CampaignConfiguration
    ): RunningCampaign {
        require(configuration.scheduledAt?.isAfter(Instant.now()) == true) {
            "The schedule time should be in the future"
        }
        val selectedScenarios = configuration.scenarios.keys
        val scenarios = factoryService.getActiveScenarios(tenant, selectedScenarios).distinctBy { it.name }
        log.trace { "Found unique scenarios: $scenarios" }
        val missingScenarios = selectedScenarios - scenarios.map { it.name }.toSet()
        require(missingScenarios.isEmpty()) { "The scenarios ${missingScenarios.joinToString()} were not found or are not currently supported by healthy factories" }

        val runningCampaign = campaignPreparator.convertAndSaveCampaign(tenant, configurer, configuration, true)
        configuration.scheduledAt?.let { schedule(runningCampaign.key, it) }

        return runningCampaign
    }

    override suspend fun update(
        tenant: String,
        configurer: String,
        campaignKey: CampaignKey,
        configuration: CampaignConfiguration
    ): RunningCampaign {
        requireNotNull(campaignRepository.findByTenantAndKeyAndScheduled(tenant, campaignKey)) {
            "Campaign does not exist"
        }
        scheduledCampaignsRegistry.cancelSchedule(campaignKey)
        return schedule(tenant, configurer, configuration)
    }

    @KTestable
    private suspend fun schedulingExecution(campaignKey: CampaignKey, instant: Instant) {
        delay(Duration.between(Instant.now(), instant).toMillis())
        val campaignEntity = campaignRepository.findByKey(campaignKey)
        val configuration = requireNotNull(campaignEntity.configuration) {
            "No configuration was found for the campaign"
        }

        val configurer = requireNotNull(userRepository.findUsernameById(campaignEntity.configurer)) {
            "The provided configurer does not exist"
        }
        val tenant = tenantRepository.findReferenceById(campaignEntity.tenantId)
        try {
            campaignExecutor.start(
                tenant = tenant,
                configurer = configurer,
                configuration = configuration.copy(name = "${campaignEntity.name} (${Instant.now()})")
            )
        } catch (e: Exception) {
            log.warn(e) { e.message }
        }
        configuration.scheduling?.let { scheduling ->
            val nextSchedule = scheduling.nextSchedule()
            log.debug { "Schedule the next execution for the campaign ${campaignEntity.name} to $nextSchedule" }
            campaignRepository.update(campaignEntity.copy(start = nextSchedule))
            schedule(campaignKey, nextSchedule)
        }
    }

    companion object {

        private val log = logger()

    }
}