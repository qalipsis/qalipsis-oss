package io.qalipsis.core.factories.steps.converters

import cool.graph.cuid.Cuid
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.steps.SingletonTubeStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factories.steps.TubeStep

/**
 * [StepSpecificationConverter] from [SingletonTubeStepSpecification] to [TubeStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class SingletonTubeStepSpecificationConverter : StepSpecificationConverter<SingletonTubeStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is SingletonTubeStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<SingletonTubeStepSpecification<*>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as SingletonTubeStepSpecification<I>
        val step = TubeStep<I>(spec.name ?: Cuid.createCuid())
        creationContext.createdStep(step)
    }

}
