package io.qalipsis.core.factories.steps.converters

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.ValidationStepSpecification
import io.qalipsis.core.factories.steps.ValidationStep

/**
 * [StepSpecificationConverter] from [ValidationStepSpecification] to [ValidationStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class ValidationStepSpecificationConverter :
    StepSpecificationConverter<ValidationStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is ValidationStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<ValidationStepSpecification<*>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as ValidationStepSpecification<I>
        val step = ValidationStep(spec.name,
            spec.retryPolicy ?: creationContext.directedAcyclicGraph.scenario.defaultRetryPolicy,
            spec.specification)
        creationContext.createdStep(step)
    }

}
