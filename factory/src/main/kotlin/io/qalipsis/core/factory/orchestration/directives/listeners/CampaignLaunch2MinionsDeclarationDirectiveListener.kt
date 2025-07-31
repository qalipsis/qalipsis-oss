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
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.MinionsAssignmentDirective
import io.qalipsis.core.directives.MinionsDeclarationDirective
import io.qalipsis.core.directives.MinionsDeclarationDirectiveReference
import io.qalipsis.core.factory.communication.DirectiveListener
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.factory.orchestration.ScenarioRegistry
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsDeclarationFeedback
import jakarta.inject.Singleton
import org.slf4j.event.Level

/**
 * The [CampaignLaunch2MinionsDeclarationDirectiveListener] is responsible for generating the identifiers for the minions to
 * create for each scenario and DAG.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
class CampaignLaunch2MinionsDeclarationDirectiveListener(
    private val scenarioRegistry: ScenarioRegistry,
    private val factoryChannel: FactoryChannel,
    private val idGenerator: IdGenerator,
    private val minionAssignmentKeeper: MinionAssignmentKeeper,
    private val factoryCampaignManager: FactoryCampaignManager
) : DirectiveListener<MinionsDeclarationDirective> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is MinionsDeclarationDirectiveReference
                && factoryCampaignManager.isLocallyExecuted(directive.campaignKey, directive.scenarioName)
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun notify(directive: MinionsDeclarationDirective) {
        val feedback = MinionsDeclarationFeedback(
            campaignKey = directive.campaignKey,
            scenarioName = directive.scenarioName,
            status = FeedbackStatus.IN_PROGRESS
        )
        factoryChannel.publishFeedback(feedback)
        try {
            declareMinions(directive.minionsCount, directive, scenarioRegistry[directive.scenarioName]!!)
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

    private suspend fun declareMinions(
        minionsCount: Int,
        directive: MinionsDeclarationDirective,
        scenario: Scenario
    ) {
        log.debug { "Creating $minionsCount minions IDs for the scenario ${directive.scenarioName}" }
        val minionsUnderLoad = mutableListOf<MinionId>()
        for (i in 0 until minionsCount) {
            minionsUnderLoad.add(generateMinionId(scenario.name))
        }
        val dagsUnderLoad = mutableListOf<DirectedAcyclicGraphName>()
        log.trace { "Registering the singleton minions to assign" }
        scenario.dags.forEach { dag ->
            val minions = mutableListOf<MinionId>()
            if (dag.isSingleton || !dag.isUnderLoad) {
                // DAGs not under load receive a unique minion.
                val minion = scenario.name + "-lonely-" + idGenerator.short()
                minions.add(minion)
                minionAssignmentKeeper.registerMinionsToAssign(
                    directive.campaignKey,
                    directive.scenarioName,
                    listOf(dag.name),
                    listOf(minion),
                    false
                )
            } else {
                dagsUnderLoad += dag.name
                minions += minionsUnderLoad
            }
        }
        log.trace { "Registering the minions under load to assign" }
        // Registers all the non-singleton minions at once.
        minionAssignmentKeeper.registerMinionsToAssign(
            directive.campaignKey,
            directive.scenarioName,
            dagsUnderLoad,
            minionsUnderLoad
        )
        log.trace { "Completing the registration of the minions to assign" }
        minionAssignmentKeeper.completeUnassignedMinionsRegistration(directive.campaignKey, directive.scenarioName)

        log.trace { "Sending the directive to proceed with the minions assignment" }
        val minionsAssignmentDirective = MinionsAssignmentDirective(
            directive.campaignKey,
            scenario.name
        )
        factoryChannel.publishDirective(minionsAssignmentDirective)
    }

    private fun generateMinionId(scenarioName: ScenarioName): MinionId {
        return scenarioName + "-" + idGenerator.short()
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
