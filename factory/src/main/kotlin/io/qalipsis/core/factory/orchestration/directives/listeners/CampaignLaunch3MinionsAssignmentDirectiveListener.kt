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
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.MinionsAssignmentDirective
import io.qalipsis.core.factory.communication.DirectiveListener
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsAssignmentFeedback
import jakarta.inject.Singleton
import org.slf4j.event.Level

@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
internal class CampaignLaunch3MinionsAssignmentDirectiveListener(
    private val minionsKeeper: MinionsKeeper,
    private val minionAssignmentKeeper: MinionAssignmentKeeper,
    private val factoryChannel: FactoryChannel,
    private val factoryCampaignManager: FactoryCampaignManager
) : DirectiveListener<MinionsAssignmentDirective> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is MinionsAssignmentDirective
                && factoryCampaignManager.isLocallyExecuted(directive.campaignKey, directive.scenarioName)
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun notify(directive: MinionsAssignmentDirective) {
        val feedback = MinionsAssignmentFeedback(
            campaignKey = directive.campaignKey,
            scenarioName = directive.scenarioName,
            status = FeedbackStatus.IN_PROGRESS
        )
        factoryChannel.publishFeedback(feedback)
        try {
            val assignedMinions = minionAssignmentKeeper.assign(directive.campaignKey, directive.scenarioName)
            if (assignedMinions.isEmpty()) {
                factoryChannel.publishFeedback(feedback.copy(status = FeedbackStatus.IGNORED))
            } else {
                assignedMinions.forEach { (minionId, dags) ->
                    minionsKeeper.create(directive.campaignKey, directive.scenarioName, dags, minionId)
                }
                factoryChannel.publishFeedback(feedback.copy(status = FeedbackStatus.COMPLETED))
            }
        } catch (e: Exception) {
            log.error(e) { e.message }
            factoryChannel.publishFeedback(feedback.copy(status = FeedbackStatus.FAILED, errorMessage = e.message ?: ""))
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
