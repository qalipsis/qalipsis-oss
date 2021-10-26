package io.qalipsis.core.factories.steps.converters

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.steps.FlatMapStepSpecification
import io.qalipsis.api.steps.MapStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factories.steps.FlatMapStep
import io.qalipsis.core.factories.steps.MapStep

/**
 * [StepSpecificationConverter] from [FlatMapStepSpecification] to [FlatMapStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class MapStepSpecificationConverter :
    StepSpecificationConverter<MapStepSpecification<*, *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is MapStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<MapStepSpecification<*, *>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as MapStepSpecification<I, O>
        val step = MapStep(spec.name,
            spec.retryPolicy ?: creationContext.directedAcyclicGraph.scenario.defaultRetryPolicy, spec.block)
        creationContext.createdStep(step)
    }

}
