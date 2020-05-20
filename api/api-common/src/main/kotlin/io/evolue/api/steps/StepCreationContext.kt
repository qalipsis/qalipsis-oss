package io.evolue.api.steps

import io.evolue.api.ScenarioSpecification
import io.evolue.api.orchestration.DirectedAcyclicGraph

/**
 *
 * @author Eric Jessé
 */
interface StepCreationContext<SPEC : StepSpecification<*, *, *>> {
    val scenarioSpecification: ScenarioSpecification
    val directedAcyclicGraph: DirectedAcyclicGraph
    val stepSpecification: SPEC
    val createdStep: Step<*, *>?

    fun createdStep(step: Step<*, *>)
}