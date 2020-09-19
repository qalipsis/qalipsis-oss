package io.evolue.core.factories.steps.converters

import cool.graph.cuid.Cuid
import io.evolue.api.annotations.StepConverter
import io.evolue.api.steps.FilterStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.core.factories.steps.FilterStep

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
        val spec = creationContext.stepSpecification as FilterStepSpecification<I>
        val step = FilterStep(spec.name ?: Cuid.createCuid(),
            spec.retryPolicy ?: creationContext.directedAcyclicGraph.scenario.defaultRetryPolicy,
            spec.specification)
        creationContext.createdStep(step)
    }


}
