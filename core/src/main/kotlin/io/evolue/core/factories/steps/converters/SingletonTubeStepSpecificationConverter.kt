package io.evolue.core.factories.steps.converters

import cool.graph.cuid.Cuid
import io.evolue.api.annotations.StepConverter
import io.evolue.api.steps.SingletonTubeStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.core.factories.steps.TubeStep

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
