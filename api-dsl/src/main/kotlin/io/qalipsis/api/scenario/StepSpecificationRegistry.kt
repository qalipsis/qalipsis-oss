/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
