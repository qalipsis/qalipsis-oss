package io.qalipsis.core.factories.steps.converters

import cool.graph.cuid.Cuid
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.TubeStepSpecification
import io.qalipsis.core.factories.steps.TubeStep

/**
 * [StepSpecificationConverter] from [TubeStepSpecification] to [TubeStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class TubeStepSpecificationConverter : StepSpecificationConverter<TubeStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is TubeStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<TubeStepSpecification<*>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as TubeStepSpecification<I>
        val step = TubeStep<I>(spec.name ?: Cuid.createCuid())
        creationContext.createdStep(step)
    }

}
