package io.qalipsis.api.scenario

import io.qalipsis.api.context.DirectedAcyclicGraphId
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
    val dagsUnderLoad: Collection<DirectedAcyclicGraphId>

    /**
     * Finds a [StepSpecification] that already exists or will soon exist.
     */
    suspend fun <O> find(stepName: StepName): StepSpecification<*, O, *>?

    /**
     * Verifies if a [StepSpecification] with the given name already exists.
     */
    fun exists(stepName: StepName): Boolean

    /**
     * Provides a unique [DirectedAcyclicGraphId].
     *
     * @param parent when the DAG follows another one, the ancestor is provided
     */
    fun buildDagId(parent: DirectedAcyclicGraphId? = null): DirectedAcyclicGraphId

    /**
     * Adds the step as root of the scenario and assign a relevant [StepSpecification.directedAcyclicGraphId].
     */
    fun add(step: StepSpecification<*, *, *>)

    /**
     * Inserts the [newRoot] at the position of [rootToShift], making the later a next of the former.
     */
    fun insertRoot(newRoot: StepSpecification<*, *, *>, rootToShift: StepSpecification<*, *, *>)

    /**
     * [register] [nextStep] in the scenario and assigns it a relevant [StepSpecification.directedAcyclicGraphId].
     *
     * This does not add [nextStep] to the list of [previousStep]'s next steps.
     */
    fun registerNext(previousStep: StepSpecification<*, *, *>, nextStep: StepSpecification<*, *, *>)

    /**
     * Adds the step to the scenario registry for later use.
     */
    fun register(step: StepSpecification<*, *, *>)
}
