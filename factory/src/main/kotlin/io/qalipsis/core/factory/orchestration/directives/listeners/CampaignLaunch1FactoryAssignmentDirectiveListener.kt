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

package io.qalipsis.core.factory.orchestration.directives.listeners

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.lang.supplyIf
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.FactoryAssignmentDirective
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.campaign.CampaignLifeCycleAware
import io.qalipsis.core.factory.communication.DirectiveListener
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.feedbacks.FactoryAssignmentFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import jakarta.inject.Singleton
import org.slf4j.event.Level
import java.time.Instant


/**
 * The [CampaignLaunch1FactoryAssignmentDirectiveListener] is responsible for saving the assignment of the DAGS
 * into the current factory for the starting scenario.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
internal class CampaignLaunch1FactoryAssignmentDirectiveListener(
    private val minionAssignmentKeeper: MinionAssignmentKeeper,
    private val campaignLifeCycleAwares: Collection<CampaignLifeCycleAware>,
    private val factoryChannel: FactoryChannel
) : DirectiveListener<FactoryAssignmentDirective> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is FactoryAssignmentDirective
    }

    @LogInputAndOutput(level = Level.DEBUG)
    override suspend fun notify(directive: FactoryAssignmentDirective) {
        val feedback = FactoryAssignmentFeedback(
            campaignKey = directive.campaignKey,
            status = FeedbackStatus.IN_PROGRESS
        )

        try {
            val campaign = Campaign(
                campaignKey = directive.campaignKey,
                speedFactor = directive.runningCampaign.speedFactor,
                startOffsetMs = directive.runningCampaign.startOffsetMs,
                softTimeout = supplyIf(directive.runningCampaign.softTimeoutSec > 0) { Instant.ofEpochSecond(directive.runningCampaign.softTimeoutSec) },
                hardTimeout = supplyIf(directive.runningCampaign.hardTimeoutSec > 0) { Instant.ofEpochSecond(directive.runningCampaign.hardTimeoutSec) },
                broadcastChannel = directive.runningCampaign.broadcastChannel,
                feedbackChannel = directive.runningCampaign.feedbackChannel,
                scenarios = directive.runningCampaign.scenarios,
                assignments = directive.assignments
            )
            campaignLifeCycleAwares.forEach {
                it.init(campaign)
            }
            factoryChannel.publishFeedback(feedback)
            minionAssignmentKeeper.assignFactoryDags(directive.campaignKey, directive.assignments)
            factoryChannel.publishFeedback(feedback.copy(status = FeedbackStatus.COMPLETED))
        } catch (e: Exception) {
            log.error(e) { e.message }
            factoryChannel.publishFeedback(
                feedback.copy(
                    status = FeedbackStatus.FAILED,
                    errorMessage = e.message ?: ""
                )
            )
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }

}