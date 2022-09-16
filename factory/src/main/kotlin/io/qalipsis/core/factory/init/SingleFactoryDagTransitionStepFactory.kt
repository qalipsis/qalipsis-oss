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

package io.qalipsis.core.factory.init

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.steps.Step
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.steps.DagTransitionStep
import io.qalipsis.core.factory.steps.DeadEndStep
import jakarta.inject.Singleton

/**
 * Implementation of [DagTransitionStepFactory] for deployments having a single factory.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.STANDALONE, ExecutionEnvironments.SINGLE_FACTORY])
internal class SingleFactoryDagTransitionStepFactory(
    private val factoryCampaignManager: FactoryCampaignManager
) : DagTransitionStepFactory {

    override fun createDeadEnd(stepName: StepName, sourceDagId: DirectedAcyclicGraphName): DeadEndStep<*> {
        return DeadEndStep<Any?>(stepName, sourceDagId, factoryCampaignManager)
    }

    override fun createTransition(
        stepName: StepName,
        sourceDagId: DirectedAcyclicGraphName,
        targetDagId: DirectedAcyclicGraphName
    ): Step<*, Any?> {
        return DagTransitionStep(
            stepName,
            sourceDagId,
            factoryCampaignManager
        )
    }
}