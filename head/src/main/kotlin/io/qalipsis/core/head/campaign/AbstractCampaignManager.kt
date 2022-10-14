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
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.campaigns.FactoryConfiguration
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.CampaignManagementDirective
import io.qalipsis.core.factory.communication.HeadChannel
import io.qalipsis.core.feedbacks.CampaignManagementFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.communication.FeedbackListener
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.Factory
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.head.orchestration.FactoryDirectedAcyclicGraphAssignmentResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.validation.constraints.NotBlank

/**
 * Service in charge of keeping track of the campaigns executions across the whole cluster.
 *
 * @author Eric Jessé
 */
internal abstract class AbstractCampaignManager<C : CampaignExecutionContext>(
    private val headChannel: HeadChannel,
    private val factoryService: FactoryService,
    private val assignmentResolver: FactoryDirectedAcyclicGraphAssignmentResolver,
    private val campaignService: CampaignService,
    private val campaignReportStateKeeper: CampaignReportStateKeeper,
    private val headConfiguration: HeadConfiguration,
    private val coroutineScope: CoroutineScope,
    private val campaignExecutionContext: C,
) : CampaignManager, FeedbackListener<Feedback> {

    private val processingMutex = Mutex()

    override fun accept(feedback: Feedback): Boolean {
        return feedback is CampaignManagementFeedback
    }

    @LogInput
    override suspend fun notify(feedback: Feedback) {
        tryAndLog(log) {
            feedback as CampaignManagementFeedback
            processingMutex.withLock {
                val sourceCampaignState = get(feedback.tenant, feedback.campaignKey)
                log.trace { "Processing $feedback on $sourceCampaignState" }
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

    override suspend fun start(
        tenant: String,
        configurer: String,
        configuration: CampaignConfiguration
    ): RunningCampaign {
        val selectedScenarios = configuration.scenarios.keys
        val scenarios = factoryService.getActiveScenarios(tenant, selectedScenarios).distinctBy { it.name }
        val missingScenarios = selectedScenarios - scenarios.map { it.name }.toSet()
        require(missingScenarios.isEmpty()) { "The scenarios ${missingScenarios.joinToString()} were not found or are not currently supported by healthy factories" }

        val runningCampaign = campaignService.create(tenant, configurer, configuration)
        try {
            val factories = factoryService.getAvailableFactoriesForScenarios(tenant, selectedScenarios)
            require(factories.isNotEmpty()) { "No available factory found to execute the campaign" }

            coroutineScope.contextualLaunch {
                try {
                    prepareAndExecute(runningCampaign, factories, configuration, tenant, selectedScenarios, scenarios)
                } catch (e: Exception) {
                    log.error(e) { "An error occurred while preparing the campaign ${runningCampaign.key} to start" }
                    campaignService.close(tenant, runningCampaign.key, ExecutionStatus.FAILED)
                    throw e
                }
            }
        } catch (e: Exception) {
            log.error(e) { "An error occurred while preparing the campaign ${runningCampaign.key} to start" }
            campaignService.close(tenant, runningCampaign.key, ExecutionStatus.FAILED)
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
        scenarios: List<ScenarioSummary>
    ) {
        log.trace { "Factories to evaluate for campaign ${runningCampaign.key}: ${factories.map(Factory::nodeId)}" }
        // Locks all the factories from a concurrent assignment resolution.
        factoryService.lockFactories(runningCampaign, factories.map(Factory::nodeId))
        val assignments = assignmentResolver.resolveFactoriesAssignments(runningCampaign, factories, scenarios)

        val start = Instant.now()
        val timeout = configuration.timeout?.let { timeoutDuration -> start + timeoutDuration }
            ?.also { timeout -> runningCampaign.timeoutSinceEpoch = timeout.epochSecond }

        campaignService.start(tenant, runningCampaign.key, start, timeout)
        selectedScenarios.forEach {
            campaignService.startScenario(tenant, runningCampaign.key, it, start)
            campaignReportStateKeeper.start(runningCampaign.key, it)
        }
        log.trace { "Factory assignment for campaign ${runningCampaign.key}: $assignments" }

        // Releases the unassigned factories to make them available for other campaigns.
        factoryService.releaseFactories(
            runningCampaign,
            (factories.map(Factory::nodeId) - assignments.rowKeySet())
        )

        runningCampaign.broadcastChannel = getBroadcastChannelName(runningCampaign)
        runningCampaign.feedbackChannel = getFeedbackChannelName(runningCampaign)

        val factoriesByNodeId = factories.associateBy(Factory::nodeId)
        assignments.rowMap().forEach { (factoryNodeId, assignments) ->
            runningCampaign.factories[factoryNodeId] = FactoryConfiguration(
                unicastChannel = factoriesByNodeId[factoryNodeId]!!.unicastChannel,
                assignment = assignments
            )
        }

        headChannel.subscribeFeedback(runningCampaign.feedbackChannel)
        val campaignStartState = create(runningCampaign)
        log.info { "Starting the campaign ${configuration.name} with scenarios ${scenarios.map { it.name }} on factories ${runningCampaign.factories.keys}" }
        campaignStartState.inject(campaignExecutionContext)
        processingMutex.withLock {
            val directives = campaignStartState.init()
            set(campaignStartState)
            directives.forEach {
                (it as? CampaignManagementDirective)?.tenant = tenant
                headChannel.publishDirective(it)
            }
        }
    }

    @LogInput
    override suspend fun abort(aborter: String, tenant: String, campaignKey: String, hard: Boolean) {
        tryAndLog(log) {
            processingMutex.withLock {
                val sourceCampaignState = get(tenant, campaignKey)
                val campaignState = sourceCampaignState.abort(AbortRunningCampaign(hard))
                log.trace { "Campaign state $campaignState" }
                campaignState.inject(campaignExecutionContext)
                val directives = campaignState.init()
                campaignService.abort(tenant, aborter, campaignKey)
                set(campaignState)
                campaignReportStateKeeper.abort(campaignKey)
                directives.forEach {
                    headChannel.publishDirective(it)
                }
            }
        }
    }

    abstract suspend fun create(
        campaign: RunningCampaign
    ): CampaignExecutionState<C>

    @LogInputAndOutput
    abstract suspend fun get(tenant: String, campaignKey: CampaignKey): CampaignExecutionState<C>

    @LogInput
    abstract suspend fun set(state: CampaignExecutionState<C>)

    @LogInput
    protected open suspend fun getBroadcastChannelName(campaign: RunningCampaign): String =
        BROADCAST_CONTEXTS_CHANNEL

    @LogInput
    protected open suspend fun getFeedbackChannelName(campaign: RunningCampaign): String =
        FEEDBACK_CONTEXTS_CHANNEL

    companion object {

        const val BROADCAST_CONTEXTS_CHANNEL = "directives-broadcast"

        const val FEEDBACK_CONTEXTS_CHANNEL = "feedbacks"

        private val log = logger()

    }
}