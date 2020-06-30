package io.evolue.api.steps

import io.evolue.api.orchestration.DirectedAcyclicGraph
import io.evolue.api.scenario.MutableScenarioSpecification

/**
 *
 * @author Eric Jessé
 */
interface StepCreationContext<SPEC : StepSpecification<*, *, *>> {
    val scenarioSpecification: MutableScenarioSpecification
    val directedAcyclicGraph: DirectedAcyclicGraph
    val stepSpecification: SPEC
    val createdStep: Step<*, *>?

    fun createdStep(step: Step<*, *>)
}
