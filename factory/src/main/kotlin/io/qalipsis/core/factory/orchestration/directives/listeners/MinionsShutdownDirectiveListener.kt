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
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.MinionsShutdownDirective
import io.qalipsis.core.factory.communication.DirectiveListener
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsShutdownFeedback
import jakarta.inject.Singleton
import org.slf4j.event.Level

/**
 * Consumes the [MinionsShutdownDirective] to shutdown all the components of the specified minions.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
internal class MinionsShutdownDirectiveListener(
    private val factoryCampaignManager: FactoryCampaignManager,
    private val minionsKeeper: MinionsKeeper,
    private val factoryChannel: FactoryChannel
) : DirectiveListener<MinionsShutdownDirective> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is MinionsShutdownDirective
                && factoryCampaignManager.isLocallyExecuted(directive.campaignKey, directive.scenarioName)
                && directive.minionIds.any(minionsKeeper::contains)
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun notify(directive: MinionsShutdownDirective) {
        val relevantMinions = directive.minionIds.filter(minionsKeeper::contains)
        if (relevantMinions.isNotEmpty()) {
            val feedback = MinionsShutdownFeedback(
                campaignKey = directive.campaignKey,
                scenarioName = directive.scenarioName,
                minionIds = relevantMinions,
                status = FeedbackStatus.IN_PROGRESS
            )
            factoryChannel.publishFeedback(feedback)
            tryAndLogOrNull(log) {
                factoryCampaignManager.shutdownMinions(directive.campaignKey, relevantMinions)
            }
            factoryChannel.publishFeedback(feedback.copy(status = FeedbackStatus.COMPLETED))
        }
    }

    private companion object {
        val log = logger()
    }
}
