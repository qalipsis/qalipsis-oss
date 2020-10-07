package io.qalipsis.core.factories.steps.converters

import cool.graph.cuid.Cuid
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.steps.FilterStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factories.steps.FilterStep

/**
 * [StepSpecificationConverter] from [FilterStepSpecification] to [FilterStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class FilterStepSpecificationConverter :
    StepSpecificationConverter<FilterStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is FilterStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<FilterStepSpecification<*>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as FilterStepSpecification<I>
        val step = FilterStep(spec.name ?: Cuid.createCuid(),
            spec.retryPolicy ?: creationContext.directedAcyclicGraph.scenario.defaultRetryPolicy,
            spec.specification)
        creationContext.createdStep(step)
    }


}
