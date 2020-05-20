package io.evolue.core.factory.steps.converters

import io.evolue.api.steps.AbstractStepSpecification
import io.evolue.api.steps.Step
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationDecoratorConverter
import io.evolue.core.factory.steps.IterativeStepDecorator
import java.time.Duration

/**
 * [StepSpecificationDecoratorConverter] from any [AbstractStepSpecification] to [IterativeStepDecorator].
 *
 * @author Eric Jessé
 */
internal class IterativeStepDecoratorSpecificationConverter :
    StepSpecificationDecoratorConverter<StepSpecification<*, *, *>>() {

    override val order: Int = 750

    override suspend fun <I, O> decorate(creationContext: StepCreationContext<StepSpecification<*, *, *>>,
                                         decoratedStep: Step<I, O>): Step<*, *> {
        val spec = creationContext.stepSpecification
        return if (spec.iterations > 0) {
            IterativeStepDecorator(spec.iterations,
                if (!spec.iterationPeriods.isNegative) spec.iterationPeriods else Duration.ZERO,
                decoratedStep)
        } else {
            decoratedStep
        }
    }


}