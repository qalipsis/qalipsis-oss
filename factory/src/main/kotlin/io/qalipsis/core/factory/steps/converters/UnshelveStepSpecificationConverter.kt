package io.qalipsis.core.factory.steps.converters

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.states.SharedStateRegistry
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.UnshelveStepSpecification
import io.qalipsis.core.factory.steps.SingularUnshelveStep
import io.qalipsis.core.factory.steps.UnshelveStep

/**
 * [StepSpecificationConverter] from [UnshelveStepSpecification] to [UnshelveStep] and [SingularUnshelveStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class UnshelveStepSpecificationConverter(
    private val sharedStateRegistry: SharedStateRegistry
) : StepSpecificationConverter<UnshelveStepSpecification<*, *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is UnshelveStepSpecification<*, *>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<UnshelveStepSpecification<*, *>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as UnshelveStepSpecification<I, O>
        val step = if (spec.singular) {
            SingularUnshelveStep<I, O>(spec.name, sharedStateRegistry, spec.names.first(),
                spec.delete)
        } else {
            UnshelveStep<I>(spec.name, sharedStateRegistry, spec.names, spec.delete)
        }
        creationContext.createdStep(step)
    }

}
