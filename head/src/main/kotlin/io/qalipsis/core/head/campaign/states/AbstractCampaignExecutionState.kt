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

package io.qalipsis.core.head.campaign.states

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.heartbeat.Heartbeat

/**
 * Parent class of all implementations of [CampaignExecutionState].
 *
 * @author Eric Jessé
 */
abstract class AbstractCampaignExecutionState<C : CampaignExecutionContext>(
    override val campaignKey: CampaignKey
) : CampaignExecutionState<C> {

    var initialized: Boolean = false

    protected lateinit var context: C

    override val isCompleted: Boolean = false

    override fun inject(context: C) {
        this.context = context
    }

    override suspend fun init(): List<Directive> {
        return if (!initialized) {
            val directives = doInit()
            initialized = true
            directives
        } else {
            emptyList()
        }
    }

    protected open suspend fun doInit(): List<Directive> = emptyList()

    override suspend fun process(feedback: Feedback): CampaignExecutionState<C> {
        return doTransition(feedback)
    }

    protected open suspend fun doTransition(feedback: Feedback): CampaignExecutionState<C> {
        return this
    }

    override suspend fun abort(abortConfiguration: AbortRunningCampaign): CampaignExecutionState<C> {
        return this
    }

    protected suspend fun abort(
        campaign: RunningCampaign,
        toDoWithResult: () -> CampaignExecutionState<CampaignExecutionContext>
    ): CampaignExecutionState<CampaignExecutionContext> {
        val healthyFactories = context.factoryService.getFactoriesHealth(campaign.tenant, campaign.factories.keys)
            .filter { it.state == Heartbeat.State.IDLE }.map { it.nodeId }.toSet()
        return if (healthyFactories.isEmpty()) {
            // No factory can acknowledge the abortion, hence the campaign is directly disabled.
            disabledState(campaign)
        } else {
            // Else only keep healthy factories and return AbortingState
            campaign.factories.keys.removeIf { it !in healthyFactories }
            toDoWithResult()
        }
    }

    /**
     * Closes the campaign with [status] and initializes [disabledState], returning its directives.
     *
     * This is used by the terminal states when no factory remains to acknowledge the shutdown of the campaign:
     * no feedback would ever come to leave them, letting the campaign run forever.
     */
    protected suspend fun terminateCampaign(
        campaign: RunningCampaign,
        status: ExecutionStatus,
        failureReason: String? = null,
        disabledState: CampaignExecutionState<CampaignExecutionContext>
    ): List<Directive> {
        log.debug { "Terminating campaign $campaign" }
        context.campaignReportStateKeeper.complete(campaignKey, status, failureReason)
        context.campaignService.close(campaign.tenant, campaignKey, status, failureReason)
        return disabledState.run {
            inject(context)
            init()
        }
    }

    /**
     * Creates the state to apply when the campaign has to be disabled without any further interaction with
     * the factories. Implementations keeping their own persistent context should return a state cleaning it.
     */
    protected open fun disabledState(campaign: RunningCampaign): CampaignExecutionState<CampaignExecutionContext> =
        DisabledState(campaign, isSuccessful = false)


    companion object {

        private val log = logger()

    }
}