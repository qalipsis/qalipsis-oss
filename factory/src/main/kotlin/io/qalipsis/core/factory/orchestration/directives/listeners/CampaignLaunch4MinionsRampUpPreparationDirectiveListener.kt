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
import io.qalipsis.core.directives.MinionsRampUpPreparationDirective
import io.qalipsis.core.directives.MinionsRampUpPreparationDirectiveReference
import io.qalipsis.core.factory.communication.DirectiveListener
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsRampUpPreparationFeedback
import jakarta.inject.Singleton
import org.slf4j.event.Level
import java.time.Duration

/**
 *
 * The [CampaignLaunch4MinionsRampUpPreparationDirectiveListener] is responsible for generating the execution profile to start all
 * the [io.qalipsis.api.orchestration.Minion]s for the execution of a scenario.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
internal class CampaignLaunch4MinionsRampUpPreparationDirectiveListener(
    private val factoryChannel: FactoryChannel,
    private val factoryCampaignManager: FactoryCampaignManager,
    private val minionAssignmentKeeper: MinionAssignmentKeeper
) : DirectiveListener<MinionsRampUpPreparationDirective> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is MinionsRampUpPreparationDirectiveReference
                && factoryCampaignManager.isLocallyExecuted(directive.campaignKey, directive.scenarioName)
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun notify(directive: MinionsRampUpPreparationDirective) {
        val feedback = MinionsRampUpPreparationFeedback(
            campaignKey = directive.campaignKey,
            scenarioName = directive.scenarioName,
            status = FeedbackStatus.IN_PROGRESS
        )
        factoryChannel.publishFeedback(feedback)
        try {
            val minionsStartDefinitions = factoryCampaignManager.prepareMinionsExecutionProfile(
                directive.campaignKey, directive.scenarioName, directive.executionProfileConfiguration
            )

            log.debug {
                val numberOfTotalMinionsToStart = minionsStartDefinitions.sumOf { it.count }
                val endOfCompleteRampUp = Duration.ofMillis(minionsStartDefinitions.last().offsetMs)
                "Ramp-up: starting $numberOfTotalMinionsToStart minions in $endOfCompleteRampUp"
            }
            minionAssignmentKeeper.schedule(directive.campaignKey, directive.scenarioName, minionsStartDefinitions)

            factoryChannel.publishFeedback(
                feedback.copy(
                    status = FeedbackStatus.COMPLETED
                )
            )
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
