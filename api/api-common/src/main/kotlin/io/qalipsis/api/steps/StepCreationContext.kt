package io.qalipsis.api.steps

import io.qalipsis.api.orchestration.DirectedAcyclicGraph
import io.qalipsis.api.scenario.MutableScenarioSpecification

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
