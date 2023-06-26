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

import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.steps.Step

internal interface DagTransitionStepFactory {

    /**
     * Creates a new step in charge or managing the transition between two directed acyclic graphs.
     */
    fun createDeadEnd(stepName: StepName, sourceDagId: DirectedAcyclicGraphName): Step<*, *>

    /**
     * Creates a new step in charge or managing the transition between two directed acyclic graphs.
     *
     * @param stepName the name of the transition step to create
     * @param sourceDagId the name of the DAG upside the step
     * @param targetDagId the name of the DAG downside the step
     * @param notifyDagCompletion indicates whether the step should notify the completion of the source dag
     *
     */
    fun createTransition(
        stepName: StepName,
        sourceDagId: DirectedAcyclicGraphName,
        targetDagId: DirectedAcyclicGraphName,
        notifyDagCompletion: Boolean
    ): Step<*, *>

}