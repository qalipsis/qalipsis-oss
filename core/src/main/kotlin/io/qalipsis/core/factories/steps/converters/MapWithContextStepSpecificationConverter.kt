package io.qalipsis.core.factories.steps.converters

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.steps.MapWithContextStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factories.steps.MapWithContextStep

/**
 * [StepSpecificationConverter] from [MapWithContextStepSpecification] to [MapWithContextStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class MapWithContextStepSpecificationConverter :
    StepSpecificationConverter<MapWithContextStepSpecification<*, *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is MapWithContextStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<MapWithContextStepSpecification<*, *>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as MapWithContextStepSpecification<I, O>
        val step = MapWithContextStep(
            spec.name,
            spec.retryPolicy ?: creationContext.directedAcyclicGraph.scenario.defaultRetryPolicy, spec.block
        )
        creationContext.createdStep(step)
    }

}
