package io.qalipsis.core.factories.steps.converters

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.steps.CollectionStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factories.steps.CollectionStep

/**
 * [StepSpecificationConverter] from [CollectionStepSpecification] to [CollectionStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class CollectionStepSpecificationConverter :
    StepSpecificationConverter<CollectionStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is CollectionStepSpecification
    }

    override suspend fun <I, O> convert(
        creationContext: StepCreationContext<CollectionStepSpecification<*>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as CollectionStepSpecification<I>
        val step = CollectionStep<I>(spec.name, spec.batchTimeout,
            if (spec.batchSize <= 0) Int.MAX_VALUE else spec.batchSize)
        creationContext.createdStep(step)
    }

}
