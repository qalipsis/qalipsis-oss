/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.head.campaign

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.coroutines.contextualLaunch
import io.qalipsis.api.lang.tryAndLog
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.CampaignManagementDirective
import io.qalipsis.core.feedbacks.CampaignManagementFeedback
import io.qalipsis.core.feedbacks.CampaignTimeoutFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.communication.FeedbackListener
import io.qalipsis.core.head.communication.HeadChannel
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.hook.CampaignHook
import io.qalipsis.core.head.lock.LockProvider
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.Factory
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import kotlinx.coroutines.CoroutineScope
import org.slf4j.event.Level
import java.time.Instant
import javax.validation.constraints.NotBlank

/**
 * Service in charge of keeping track of the campaigns executions across the whole cluster.
 *
 * @author Eric Jessé
 */
internal abstract class AbstractCampaignExecutor<C : CampaignExecutionContext>(
    private val headChannel: HeadChannel,
    private val factoryService: FactoryService,
    private val campaignService: CampaignService,
    private val campaignReportStateKeeper: CampaignReportStateKeeper,
    private val coroutineScope: CoroutineScope,
    private val campaignExecutionContext: C,
    private val campaignConstraintsProvider: CampaignConstraintsProvider,
    private val campaignHooks: Collection<CampaignHook>,
    private val lockProvider: LockProvider,
    private val channelNameFactory: ChannelNameFactory
) : CampaignExecutor, FeedbackListener<Feedback> {

    override fun accept(feedback: Feedback): Boolean {
        return feedback is CampaignManagementFeedback
    }

    @LogInput
    override suspend fun notify(feedback: Feedback) {
        tryAndLog(log) {
            feedback as CampaignManagementFeedback
            lockProvider.withLock(feedback.campaignKey) {
                val sourceCampaignState = get(feedback.tenant, feedback.campaignKey)
                log.trace { "Processing $feedback on $sourceCampaignState" }
                if (feedback is CampaignTimeoutFeedback) {
                    timeoutAbort(feedback.tenant, campaignKey = feedback.campaignKey, hard = feedback.hard)
                } else {
                    val campaignState = sourceCampaignState.process(feedback)
                    log.trace { "New campaign state $campaignState" }
                    campaignState.inject(campaignExecutionContext)
                    val directives = campaignState.init()
                    set(campaignState)
                    directives.forEach {
                        (it as? CampaignManagementDirective)?.tenant = feedback.tenant
                        headChannel.publishDirective(it)
                    }
                }
            }
        }
    }

    override suspend fun start(
        tenant: String,
        configurer: String,
        configuration: CampaignConfiguration,
    ): RunningCampaign {
        val selectedScenarios = configuration.scenarios.keys
        val scenarios = factoryService.getActiveScenarios(tenant, selectedScenarios).distinctBy { it.name }
        log.trace { "Found unique scenarios: $scenarios" }
        val missingScenarios = selectedScenarios - scenarios.map { it.name }.toSet()
        require(missingScenarios.isEmpty()) { "The scenarios ${missingScenarios.joinToString()} were not found or are not currently supported by healthy factories" }

        val runningCampaign = campaignService.create(tenant, configurer, configuration)
        try {
            val factories = factoryService.getAvailableFactoriesForScenarios(tenant, selectedScenarios)
            coroutineScope.contextualLaunch {
                try {
                    prepareAndExecute(runningCampaign, factories, configuration, tenant, selectedScenarios, scenarios)
                } catch (e: Exception) {
                    log.error(e) { "An error occurred while preparing the campaign ${runningCampaign.key} to start" }
                    campaignReportStateKeeper.complete(runningCampaign.key, ExecutionStatus.FAILED, e.message)
                    campaignService.close(tenant, runningCampaign.key, ExecutionStatus.FAILED, e.message)
                    throw e
                }
            }
        } catch (e: Exception) {
            log.error(e) { "An error occurred while preparing the campaign ${runningCampaign.key} to start" }
            tryAndLogOrNull(log) {
                campaignReportStateKeeper.complete(runningCampaign.key, ExecutionStatus.FAILED, e.message)
                campaignService.close(tenant, runningCampaign.key, ExecutionStatus.FAILED, e.message)
            }
            throw e
        }
        return runningCampaign
    }

    private suspend fun prepareAndExecute(
        runningCampaign: RunningCampaign,
        factories: Collection<Factory>,
        configuration: CampaignConfiguration,
        tenant: String,
        selectedScenarios: Set<@NotBlank ScenarioName>,
        scenarios: List<ScenarioSummary>,
    ) {
        campaignService.prepare(tenant, runningCampaign.key)
        runningCampaign.broadcastChannel = channelNameFactory.getBroadcastChannelName(campaign = runningCampaign)
        runningCampaign.feedbackChannel = channelNameFactory.getFeedbackChannelName(campaign = runningCampaign)
        headChannel.subscribeFeedback(runningCampaign.feedbackChannel)

        val start = Instant.now()
        val defaultCampaignConfiguration = campaignConstraintsProvider.supply(tenant)

        val (softTimeout, hardTimeout) = if (configuration.hardTimeout == true) {
            null to (start + configuration.timeout?.coerceAtMost(defaultCampaignConfiguration.validation.maxExecutionDuration))
        } else {
            configuration.timeout?.let { start + it } to (start + defaultCampaignConfiguration.validation.maxExecutionDuration)
        }
        runningCampaign.hardTimeoutSec = hardTimeout.epochSecond
        runningCampaign.softTimeoutSec = softTimeout?.takeIf { it < hardTimeout }?.epochSecond ?: -1
        campaignHooks.forEach {
            it.preStart(runningCampaign)
        }
        campaignService.start(tenant, runningCampaign.key, start, softTimeout, hardTimeout)

        selectedScenarios.forEach {
            campaignService.startScenario(tenant, runningCampaign.key, it, start)
            campaignReportStateKeeper.start(runningCampaign.key, it)
        }
        log.debug { "Initializing the state for the campaign ${runningCampaign.key}" }
        val campaignStartState = createInitialState(runningCampaign, factories, scenarios)
        log.info { "Starting the campaign ${configuration.name} with key ${runningCampaign.key} with scenarios ${scenarios.map { it.name }} on factories ${factories.map { it.nodeId }}" }
        campaignStartState.inject(campaignExecutionContext)
        lockProvider.withLock(campaignKey = runningCampaign.key) {
            log.trace { "Initializing the campaign state for start $campaignStartState" }
            val directives = campaignStartState.init()
            set(campaignStartState)
            directives.forEach {
                (it as? CampaignManagementDirective)?.tenant = tenant
                headChannel.publishDirective(it)
            }
        }
    }

    @LogInput
    override suspend fun abort(tenant: String, aborter: String, campaignKey: String, hard: Boolean) {
        tryAndLog(log) {
            lockProvider.withLock(campaignKey) {
                val sourceCampaignState = get(tenant, campaignKey)
                val campaignState = sourceCampaignState.abort(AbortRunningCampaign(hard))
                log.trace { "Campaign state $campaignState" }
                campaignState.inject(campaignExecutionContext)
                campaignService.abort(tenant, aborter, campaignKey)
                if (hard) {
                    campaignReportStateKeeper.abort(campaignKey)
                }
                val directives = campaignState.init()
                if (!campaignState.isCompleted) {
                    set(campaignState)
                    directives.forEach {
                        headChannel.publishDirective(it)
                    }
                }
            }
        }
    }

    override suspend fun replay(tenant: String, configurer: String, campaignKey: String): RunningCampaign {
        val campaign = campaignService.retrieveConfiguration(tenant, campaignKey)
        return start(tenant, configurer, campaign)
    }

    @LogInputAndOutput(level = Level.DEBUG)
    abstract suspend fun createInitialState(
        campaign: RunningCampaign,
        factories: Collection<Factory>,
        scenarios: List<ScenarioSummary>,
    ): CampaignExecutionState<C>

    @LogInputAndOutput
    abstract suspend fun get(tenant: String, campaignKey: CampaignKey): CampaignExecutionState<C>

    @LogInput
    abstract suspend fun set(state: CampaignExecutionState<C>)

    /**
     * Custom abort function to abort a campaign softly or hardly when a
     * [CampaignTimeoutFeedback] is published.
     *
     * @param tenant reference of the tenant owing the campaign
     * @param campaignKey reference of the running campaign
     * @param hard specifies if the abort is a soft or hard one
     */
    private suspend fun timeoutAbort(tenant: String, campaignKey: String, hard: Boolean) {
        tryAndLog(log) {
            val sourceCampaignState = get(tenant, campaignKey)
            val campaignState = sourceCampaignState.abort(AbortRunningCampaign(hard))
            log.trace { "Campaign state $campaignState" }
            campaignState.inject(campaignExecutionContext)
            val directives = campaignState.init()
            campaignService.abort(tenant, null, campaignKey)
            set(campaignState)
            if (hard) {
                campaignReportStateKeeper.abort(campaignKey)
            }
            directives.forEach {
                headChannel.publishDirective(it)
            }
        }
    }

    companion object {

        private val log = logger()

    }
}