/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
 *
 */

package io.qalipsis.core.factory.init

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.steps.Step
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.steps.DagTransitionStep
import io.qalipsis.core.factory.steps.DeadEndStep
import io.qalipsis.core.factory.steps.WorkflowStepDecorator
import jakarta.inject.Singleton

/**
 * Implementation of [DagTransitionStepFactory] for deployments having a single factory.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(missingBeans = [DagTransitionStepFactory::class])
class SingleFactoryDagTransitionStepFactory(
    private val factoryCampaignManager: FactoryCampaignManager,
    private val reportLiveStateRegistry: CampaignReportLiveStateRegistry,
) : DagTransitionStepFactory {

    override fun createDeadEnd(stepName: StepName, sourceDagId: DirectedAcyclicGraphName): Step<*, *> {
        return WorkflowStepDecorator(
            DeadEndStep<Any?>(stepName, sourceDagId, factoryCampaignManager),
            reportLiveStateRegistry
        )
    }

    override fun createTransition(
        stepName: StepName,
        sourceDagId: DirectedAcyclicGraphName,
        targetDagId: DirectedAcyclicGraphName,
        notifyDagCompletion: Boolean
    ): Step<*, Any?> {
        return WorkflowStepDecorator(
            DagTransitionStep(
                stepName,
                sourceDagId,
                factoryCampaignManager,
                notifyDagCompletion
            ),
            reportLiveStateRegistry
        )
    }
}