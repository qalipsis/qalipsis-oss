package io.evolue.api.steps

import io.evolue.api.orchestration.DirectedAcyclicGraph
import io.evolue.api.scenario.ScenarioSpecification

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