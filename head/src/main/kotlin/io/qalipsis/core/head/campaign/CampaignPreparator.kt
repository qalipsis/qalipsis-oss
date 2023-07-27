package io.qalipsis.core.head.campaign

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.hook.CampaignHook
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.converter.CampaignConfigurationConverter
import jakarta.inject.Singleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.count

/**
 * Service in charge of preparing campaigns.
 *
 * @author Joël Valère
 */
@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
)
internal class CampaignPreparator(
    private val userRepository: UserRepository,
    private val tenantRepository: TenantRepository,
    private val campaignRepository: CampaignRepository,
    private val campaignScenarioRepository: CampaignScenarioRepository,
    private val campaignConfigurationConverter: CampaignConfigurationConverter,
    private val hooks: List<CampaignHook>
) {

    private val campaignScheduleKeyStore: MutableMap<CampaignKey, Job> = mutableMapOf()

    /**
     * Convert and save a campaign during its creation or scheduling.
     *
     * @param tenant tenant owning the campaign to schedule
     * @param configurer consider the user's name who configure the campaign
     * @param configuration configuration of the campaign to save
     */
    suspend fun convertAndSaveCampaign(
        tenant: String,
        configurer: String,
        configuration: CampaignConfiguration,
        isScheduled: Boolean = false
    ): RunningCampaign {

        val runningCampaign = campaignConfigurationConverter.convertConfiguration(tenant, configuration)
        if (isScheduled) {
            hooks.forEach { hook -> hook.preSchedule(configuration, runningCampaign) }
        } else {
            hooks.forEach { hook -> hook.preCreate(configuration, runningCampaign) }
        }
        val campaign = campaignRepository.save(
            // The timeouts are not set yet, they will be updated when the campaign will start.
            CampaignEntity(
                tenantId = tenantRepository.findIdByReference(tenant),
                key = runningCampaign.key,
                name = configuration.name,
                scheduledMinions = runningCampaign.scenarios.values.sumOf { it.minionsCount },
                speedFactor = configuration.speedFactor,
                configurer = requireNotNull(userRepository.findIdByUsername(configurer)),
                configuration = configuration,
                result = if (isScheduled) ExecutionStatus.SCHEDULED else ExecutionStatus.QUEUED,
                start = if (isScheduled) configuration.scheduledAt else null
            )
        )

        campaignScenarioRepository.saveAll(runningCampaign.scenarios.map { (scenarioName, scenario) ->
            CampaignScenarioEntity(campaignId = campaign.id, name = scenarioName, minionsCount = scenario.minionsCount)
        }).count()
        return runningCampaign
    }

    /**
     * Cancels a scheduled test campaign job.
     *
     * @param campaignKey identifier to the job to be cancelled
     */
    suspend fun cancelSchedule(campaignKey: CampaignKey) {
        campaignScheduleKeyStore[campaignKey]?.cancel()
        campaignScheduleKeyStore.remove(campaignKey)
    }

    /**
     * Updates the campaign schedule keystore.
     *
     * @param campaignKey identifier to the job to be added to the store
     * @param scheduleJob background job that handles scheduling of the test campaign
     */
    suspend fun updateSchedule(campaignKey: CampaignKey, scheduleJob: Job) {
        campaignScheduleKeyStore[campaignKey] = scheduleJob
    }

}