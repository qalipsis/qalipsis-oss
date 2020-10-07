package io.qalipsis.core.factories.steps.converters

import cool.graph.cuid.Cuid
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.steps.OnEachStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factories.steps.OnEachStep

/**
 * [StepSpecificationConverter] from [OnEachStepSpecification] to [OnEachStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class OnEachStepSpecificationConverter : StepSpecificationConverter<OnEachStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is OnEachStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<OnEachStepSpecification<*>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as OnEachStepSpecification<I>
        val step = OnEachStep(spec.name ?: Cuid.createCuid(),
                spec.retryPolicy ?: creationContext.directedAcyclicGraph.scenario.defaultRetryPolicy,
                spec.statement)
        creationContext.createdStep(step)
    }


}
