package io.evolue.api.steps

import io.evolue.api.orchestration.DirectedAcyclicGraph
import io.evolue.api.scenario.MutableScenarioSpecification
import io.micronaut.core.annotation.Introspected
import javax.validation.Valid

/**
 * Mutable context to convert a [StepSpecification] to a [Step].
 *
 * @author Eric Jessé
 */
@Introspected
class StepCreationContextImpl<SPEC : StepSpecification<*, *, *>>(

        override val scenarioSpecification: MutableScenarioSpecification,

        override val directedAcyclicGraph: DirectedAcyclicGraph,

        override val stepSpecification: @Valid SPEC

) : StepCreationContext<SPEC> {

    override var createdStep: Step<*, *>? = null

    override fun createdStep(step: Step<*, *>) {
        createdStep = step
    }

}
