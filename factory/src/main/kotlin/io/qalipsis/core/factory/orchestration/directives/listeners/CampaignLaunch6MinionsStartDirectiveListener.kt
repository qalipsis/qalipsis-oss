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
import io.qalipsis.core.directives.MinionsStartDirective
import io.qalipsis.core.factory.communication.DirectiveListener
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.LocalAssignmentStore
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsStartFeedback
import jakarta.inject.Singleton
import org.slf4j.event.Level

/**
 * Consumes the MinionsStartDirective and schedules the start of the related minions if they are under load and have
 * their roots in the local factory.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
internal class CampaignLaunch6MinionsStartDirectiveListener(
    private val localAssignmentStore: LocalAssignmentStore,
    private val minionsKeeper: MinionsKeeper,
    private val minionsAssignmentKeeper: MinionAssignmentKeeper,
    private val factoryChannel: FactoryChannel
) : DirectiveListener<MinionsStartDirective> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is MinionsStartDirective && localAssignmentStore.hasMinionsAssigned(directive.scenarioName)
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun notify(directive: MinionsStartDirective) {
        try {
            log.debug { "Starting the singletons for the campaign ${directive.campaignKey} on scenario ${directive.scenarioName}" }
            minionsKeeper.startSingletons(directive.scenarioName)

            val scheduleOfLocalRootedMinions =
                minionsAssignmentKeeper.readSchedulePlan(directive.campaignKey, directive.scenarioName)
                    .mapValues { (_, minionIds) ->
                        minionIds.filter { minionId ->
                            localAssignmentStore.hasRootUnderLoadLocally(directive.scenarioName, minionId).also {
                                if (it) {
                                    "The minion $minionId is running the root DAG of the scenario ${directive.scenarioName} locally"
                                } else {
                                    "The minion $minionId is not running the root DAG of the scenario ${directive.scenarioName} locally"
                                }
                            }
                        }
                    }
                    .filter { (_, minionIds) -> minionIds.isNotEmpty() }

            if (scheduleOfLocalRootedMinions.isNotEmpty()) {
                val feedback = MinionsStartFeedback(
                    campaignKey = directive.campaignKey,
                    scenarioName = directive.scenarioName,
                    status = FeedbackStatus.IN_PROGRESS
                )
                factoryChannel.publishFeedback(feedback)
                scheduleOfLocalRootedMinions.toSortedMap().forEach { (offsetMs, minionIds) ->
                    minionsKeeper.scheduleMinionStart(directive.startTimestamp.plusMillis(offsetMs), minionIds)
                }
                factoryChannel.publishFeedback(feedback.copy(status = FeedbackStatus.COMPLETED))
            } else {
                log.trace { "None of the minion to start are in the local factory" }
                val feedback = MinionsStartFeedback(
                    campaignKey = directive.campaignKey,
                    scenarioName = directive.scenarioName,
                    status = FeedbackStatus.IGNORED
                )
                factoryChannel.publishFeedback(feedback)
            }
        } catch (e: Exception) {
            log.error(e) { e.message }
            factoryChannel.publishFeedback(
                MinionsStartFeedback(
                    campaignKey = directive.campaignKey,
                    scenarioName = directive.scenarioName,
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
