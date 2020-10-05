package io.evolue.core.factories.steps.converters

import cool.graph.cuid.Cuid
import io.evolue.api.annotations.StepConverter
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.api.steps.TubeStepSpecification
import io.evolue.core.factories.steps.TubeStep

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
