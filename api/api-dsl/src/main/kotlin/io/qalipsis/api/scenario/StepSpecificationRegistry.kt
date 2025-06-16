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
import io.qalipsis.api.context.StepName
import io.qalipsis.api.steps.StepSpecification

/**
 * Down-casted interface of a [ScenarioSpecification], as usable by the extension functions defined on a [ScenarioSpecification] to add a [StepSpecification].
 *
 * It should not be visible to the scenario developer in order to keep its integrity.
 *
 * @author Eric Jessé
 */
interface StepSpecificationRegistry : ScenarioSpecification {

    /**
     * [StepSpecification]s defined at the root of the scenario, as starts of the different trees composing the scenario.
     */
    val rootSteps: List<StepSpecification<*, *, *>>

    /**
     * IDs of all the DAGs receiving the load of the minions.
     */
    val dagsUnderLoad: Collection<DirectedAcyclicGraphName>

    /**
     * Finds a [StepSpecification] that already exists or will soon exist.
     */
    suspend fun <O> find(stepName: StepName): StepSpecification<*, O, *>?

    /**
     * Verifies if a [StepSpecification] with the given name already exists.
     */
    fun exists(stepName: StepName): Boolean

    /**
     * Provides a unique [DirectedAcyclicGraphName].
     *
     * @param parent when the DAG follows another one, the ancestor is provided
     */
    fun buildDagId(parent: DirectedAcyclicGraphName? = null): DirectedAcyclicGraphName

    /**
     * Adds the step as root of the scenario and assign a relevant [StepSpecification.directedAcyclicGraphName].
     */
    fun add(step: StepSpecification<*, *, *>)

    /**
     * Inserts the [newRoot] at the position of [rootToShift], making the later a next of the former.
     */
    fun insertRoot(newRoot: StepSpecification<*, *, *>, rootToShift: StepSpecification<*, *, *>)

    /**
     * [register] [nextStep] in the scenario and assigns it a relevant [StepSpecification.directedAcyclicGraphName].
     *
     * This does not add [nextStep] to the list of [previousStep]'s next steps.
     */
    fun registerNext(previousStep: StepSpecification<*, *, *>, nextStep: StepSpecification<*, *, *>)

    /**
     * Adds the step to the scenario registry for later use.
     */
    fun register(step: StepSpecification<*, *, *>)
}
