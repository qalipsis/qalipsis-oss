package io.evolue.core.factories.steps.converters

import cool.graph.cuid.Cuid
import io.evolue.api.annotations.StepConverter
import io.evolue.api.steps.DelayStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.core.factories.steps.DelayStep

/**
 * [StepSpecificationConverter] from [DelayStepSpecification] to [DelayStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class DelayedStepSpecificationConverter : StepSpecificationConverter<DelayStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is DelayStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<DelayStepSpecification<*>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as DelayStepSpecification<I>
        val step = DelayStep<I>(spec.name ?: Cuid.createCuid(), spec.duration)
        creationContext.createdStep(step)
    }


}
