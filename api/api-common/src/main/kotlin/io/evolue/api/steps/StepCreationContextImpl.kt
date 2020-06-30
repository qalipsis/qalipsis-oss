package io.evolue.api.steps

import io.evolue.api.orchestration.DirectedAcyclicGraph
import io.evolue.api.scenario.MutableScenarioSpecification

/**
 * Mutable context to convert a [StepSpecification] to a [Step].
 *
 * @author Eric Jessé
 */
class StepCreationContextImpl<SPEC : StepSpecification<*, *, *>>(

    override val scenarioSpecification: MutableScenarioSpecification,

    override val directedAcyclicGraph: DirectedAcyclicGraph,

    override val stepSpecification: SPEC

) : StepCreationContext<SPEC> {

    override var createdStep: Step<*, *>? = null

    override fun createdStep(step: Step<*, *>) {
        createdStep = step
    }

}
