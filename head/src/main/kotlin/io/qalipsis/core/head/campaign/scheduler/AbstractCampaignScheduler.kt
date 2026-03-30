package io.qalipsis.core.head.campaign.scheduler

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.ExecutionStatus.SCHEDULED
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.head.campaign.CampaignExecutor
import io.qalipsis.core.head.campaign.CampaignPreparator
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.security.TenantProvider
import io.qalipsis.core.head.security.UserProvider
import io.qalipsis.core.lifetime.HeadStartupComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Base implementation for [CampaignScheduler] providing the shared scheduling, update,
 * and campaign execution logic. Concrete subclasses implement the actual scheduling mechanism
 * and cancellation strategy.
 *
 * @author Joël Valère
 */
abstract class AbstractCampaignScheduler(
    private val userProvider: UserProvider,
    private val campaignExecutor: CampaignExecutor,
    private val tenantProvider: TenantProvider,
    private val campaignRepository: CampaignRepository,
    private val factoryService: FactoryService,
    private val campaignPreparator: CampaignPreparator,
    protected val coroutineScope: CoroutineScope
) : CampaignScheduler, HeadStartupComponent {

    /**
     * Retrieve all the configurations of campaignEntities where the result is [SCHEDULED] and schedule these campaigns.
     */
    override fun init() {
        coroutineScope.launch {
            val campaigns = campaignRepository.findByResult(result = SCHEDULED)
            log.debug { "Found ${campaigns.size} campaigns to schedule" }
            campaigns.forEach { campaign ->
                if (campaign.start?.isBefore(Instant.now()) == true) {
                    log.debug { "Scheduling campaign ${campaign.key} with immediate start" }
                    schedule(campaign.key, Instant.now())
                } else if (campaign.start?.isAfter(Instant.now()) == true) {
                    log.debug { "Scheduling campaign ${campaign.key} with start time ${campaign.start}" }
                    schedule(campaign.key, campaign.start)
                }
            }
        }
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
        require(missingScenarios.isEmpty()) {
            "The scenarios ${missingScenarios.joinToString()} were not found or are not currently supported by healthy factories"
        }

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
        cancelSchedule(campaignKey)
        return schedule(tenant, configurer, configuration)
    }

    /**
     * Cancels an existing schedule for the given campaign.
     */
    protected abstract suspend fun cancelSchedule(campaignKey: CampaignKey)

    /**
     * Executes a scheduled campaign: loads the configuration from the database, starts the campaign,
     * and either reschedules the next occurrence or deletes the campaign entity.
     */
    @KTestable
    protected suspend fun executeCampaign(campaignKey: CampaignKey) {
        val campaignEntity = campaignRepository.findByKey(campaignKey)
        val configuration = requireNotNull(campaignEntity.configuration) {
            "No configuration was found for the campaign"
        }

        val configurer = requireNotNull(userProvider.findUsernameById(campaignEntity.configurer)) {
            "The provided configurer does not exist"
        }
        val tenant = tenantProvider.findReferenceById(campaignEntity.tenantId)
        try {
            // If the campaign has a scheduling, the name is updated to include the current date.
            val campaignName = if (configuration.scheduling != null) {
                val instantPart =
                    instantCleanerRegex.find("${Instant.now()}")!!.groupValues.first().replace("T", ", ")
                "${campaignEntity.name} ($instantPart)"
            } else {
                campaignEntity.name
            }
            campaignExecutor.start(
                tenant = tenant,
                configurer = configurer,
                configuration = configuration.copy(name = campaignName)
            )
        } catch (e: Exception) {
            log.warn(e) { e.message }
        } finally {
            if (configuration.scheduling != null) {
                // If a new campaign has to be scheduled, the next start is calculated and the next execution scheduled.
                val scheduling = configuration.scheduling
                val nextSchedule = scheduling.nextSchedule()
                log.debug { "Schedule the next execution for the campaign ${campaignEntity.name} to $nextSchedule" }
                campaignRepository.update(campaignEntity.copy(start = nextSchedule, result = SCHEDULED))
                schedule(campaignKey, nextSchedule)
            } else {
                // When no other scheduling is defined, the campaign is deleted, since another campaign with the
                // same name has just been created.
                campaignRepository.delete(campaignEntity)
            }
        }
    }

    companion object {

        /**
         * Regex to remove the part of an instant under the minute.
         */
        private val instantCleanerRegex = Regex(".*T[0-9]{2}:[0-9]{2}")

        private val log = logger()

    }
}
