package io.qalipsis.api.steps

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.orchestration.DirectedAcyclicGraph
import io.qalipsis.api.scenario.StepSpecificationRegistry
import javax.validation.Valid

/**
 * Mutable context to convert a [StepSpecification] to a [Step].
 *
 * @author Eric Jessé
 */
@Introspected
class StepCreationContextImpl<SPEC : StepSpecification<*, *, *>>(

        override val scenarioSpecification: StepSpecificationRegistry,

        override val directedAcyclicGraph: DirectedAcyclicGraph,

        override val stepSpecification: @Valid SPEC

) : StepCreationContext<SPEC> {

    override var createdStep: Step<*, *>? = null

    override fun createdStep(step: Step<*, *>) {
        createdStep = step
    }

}
