package io.qalipsis.core.factories.steps.converters

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.steps.SimpleStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factories.steps.SimpleStep

/**
 * [StepSpecificationConverter] from [SimpleStepSpecification] to [SimpleStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class SimpleStepSpecificationConverter : StepSpecificationConverter<SimpleStepSpecification<*, *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is SimpleStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<SimpleStepSpecification<*, *>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as SimpleStepSpecification<I, O>
        val step = SimpleStep(spec.name,
            spec.retryPolicy ?: creationContext.directedAcyclicGraph.scenario.defaultRetryPolicy,
            spec.specification)
        creationContext.createdStep(step)
    }

}
