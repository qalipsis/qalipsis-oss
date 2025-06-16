/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.api.scenario

import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.steps.StepSpecification

/**
 * Wrapper of a scenario to introduce the beginning of the "loaded tree": the subpart of the scenario, which will
 * received the load of the minions.
 *
 * @author Eric Jessé
 */
internal class StartScenarioSpecificationWrapper(
    private val delegated: StepSpecificationRegistry,
    private val dagId: DirectedAcyclicGraphName
) : StepSpecificationRegistry by delegated {

    override fun add(step: StepSpecification<*, *, *>) {
        step.directedAcyclicGraphName = dagId
        delegated.add(step)
    }

    override fun insertRoot(newRoot: StepSpecification<*, *, *>, rootToShift: StepSpecification<*, *, *>) {
        newRoot.directedAcyclicGraphName = dagId
        delegated.insertRoot(newRoot, rootToShift)
    }
}
