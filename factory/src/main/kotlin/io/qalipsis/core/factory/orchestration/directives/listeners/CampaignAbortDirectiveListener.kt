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
import io.qalipsis.core.directives.CampaignAbortDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.factory.communication.DirectiveListener
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.feedbacks.CampaignAbortFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import jakarta.inject.Singleton
import org.slf4j.event.Level

/**
 * Consumes the [CampaignAbortDirective] to shutdown all the components related to the campaign.
 *
 * @author Svetlana Paliashchuk
 */
@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
class CampaignAbortDirectiveListener(
    private val factoryCampaignManager: FactoryCampaignManager,
    private val factoryChannel: FactoryChannel
) : DirectiveListener<CampaignAbortDirective> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is CampaignAbortDirective
                && factoryCampaignManager.isLocallyExecuted(directive.campaignKey)
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun notify(directive: CampaignAbortDirective) {
        val feedback = CampaignAbortFeedback(
            campaignKey = directive.campaignKey,
            status = FeedbackStatus.IN_PROGRESS,
            scenarioNames = directive.scenarioNames
        )
        factoryChannel.publishFeedback(feedback)

        try {
            directive.scenarioNames.forEach {
                factoryCampaignManager.shutdownScenario(directive.campaignKey, it)
            }
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
        private val log = logger()
    }

}
