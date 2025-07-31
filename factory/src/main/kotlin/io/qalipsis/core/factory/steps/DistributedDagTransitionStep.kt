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

package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.steps.ErrorProcessingStep
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.LocalAssignmentStore

/**
 * Steps at the end of a DAG when the deployment is distributed.
 *
 * @author Eric Jessé
 */
class DistributedDagTransitionStep<I>(
    id: StepName,
    dagId: DirectedAcyclicGraphName,
    private val nextDagId: DirectedAcyclicGraphName,
    factoryCampaignManager: FactoryCampaignManager,
    private val localAssignmentStore: LocalAssignmentStore,
    private val contextForwarder: ContextForwarder,
    notifyDagCompletion: Boolean
) : DagTransitionStep<I>(id, dagId, factoryCampaignManager, notifyDagCompletion), ErrorProcessingStep<I, I> {

    override suspend fun execute(context: StepContext<I, I>) {
        if (localAssignmentStore.isLocal(context.scenarioName, context.minionId, nextDagId)) {
            super<DagTransitionStep>.execute(context)
        } else {
            contextForwarder.forward(context, listOf(nextDagId))
        }
    }

    override suspend fun complete(completionContext: CompletionContext) {
        super<DagTransitionStep>.complete(completionContext)
        if (!localAssignmentStore.isLocal(completionContext.scenarioName, completionContext.minionId, nextDagId)) {
            contextForwarder.forward(completionContext, listOf(nextDagId))
        }
    }

}
