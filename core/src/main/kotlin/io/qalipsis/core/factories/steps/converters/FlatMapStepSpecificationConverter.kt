package io.qalipsis.core.factories.steps.converters

import cool.graph.cuid.Cuid
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.steps.FlatMapStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factories.steps.FlatMapStep

/**
 * [StepSpecificationConverter] from [FlatMapStepSpecification] to [FlatMapStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class FlatMapStepSpecificationConverter :
    StepSpecificationConverter<FlatMapStepSpecification<*, *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is FlatMapStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<FlatMapStepSpecification<*, *>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as FlatMapStepSpecification<I, O>
        val step = FlatMapStep(spec.name ?: Cuid.createCuid(),
            spec.retryPolicy ?: creationContext.directedAcyclicGraph.scenario.defaultRetryPolicy, spec.block)
        creationContext.createdStep(step)
    }


}
