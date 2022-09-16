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
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.factory.communication.DirectiveListener
import io.qalipsis.core.factory.orchestration.LocalAssignmentStore
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.factory.orchestration.Runner
import io.qalipsis.core.factory.orchestration.ScenarioRegistry
import io.qalipsis.core.factory.orchestration.TransportableCompletionContext
import io.qalipsis.core.factory.orchestration.TransportableContext
import io.qalipsis.core.factory.orchestration.TransportableStepContext
import io.qalipsis.core.serialization.DistributionSerializer
import jakarta.inject.Singleton


@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY])
internal class ContextDirectiveListener(
    private val distributionSerializer: DistributionSerializer,
    private val scenarioRegistry: ScenarioRegistry,
    private val minionsKeeper: MinionsKeeper,
    private val localAssignmentStore: LocalAssignmentStore,
    private val runner: Runner,
) : DirectiveListener<TransportableContext> {

    override fun accept(directive: Directive): Boolean {
        return directive is TransportableContext
    }

    override suspend fun notify(directive: TransportableContext) {
        if (directive is TransportableStepContext) {
            executeStep(directive)
        } else if (directive is TransportableCompletionContext) {
            completeStep(directive)
        }
    }

    /**
     * Executes a step and its descendants using the [TransportableStepContext].
     */
    private suspend fun executeStep(transportableContext: TransportableStepContext) {
        val minion = minionsKeeper[transportableContext.minionId]
        val step = scenarioRegistry[transportableContext.scenarioName]!!.findStep(transportableContext.stepName)!!.first
        val input = transportableContext.input?.let { distributionSerializer.deserializeRecord<Any?>(it) }
        val stepExecutionContext = transportableContext.toContext(input, transportableContext.input != null)
        runner.runMinion(minion, step, stepExecutionContext)
    }

    /**
     * Completes a step and its descendants using the [TransportableCompletionContext].
     */
    private suspend fun completeStep(transportableContext: TransportableCompletionContext) {
        val minion = minionsKeeper[transportableContext.minionId]
        val completionContext = transportableContext.toContext()

        scenarioRegistry[transportableContext.scenarioName]?.dags?.asSequence()?.filter {
            localAssignmentStore.isLocal(transportableContext.scenarioName, transportableContext.minionId, it.name)
        }?.forEach { dag ->
            runner.complete(minion, dag.rootStep.forceGet(), completionContext)
        }
    }
}