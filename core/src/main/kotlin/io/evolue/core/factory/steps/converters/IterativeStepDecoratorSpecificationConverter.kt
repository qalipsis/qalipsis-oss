package io.evolue.core.factory.steps.converters

import io.evolue.api.steps.AbstractStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationDecoratorConverter
import io.evolue.core.factory.steps.IterativeStepDecorator
import java.time.Duration
import javax.inject.Singleton

/**
 * [StepSpecificationDecoratorConverter] from any [AbstractStepSpecification] to [IterativeStepDecorator].
 *
 * @author Eric Jessé
 */
@Singleton
internal class IterativeStepDecoratorSpecificationConverter :
    StepSpecificationDecoratorConverter<StepSpecification<*, *, *>>() {

    override val order: Int = 750

    override suspend fun decorate(creationContext: StepCreationContext<StepSpecification<*, *, *>>) {
        val spec = creationContext.stepSpecification
        if (spec.iterations > 1) {
            creationContext.createdStep(IterativeStepDecorator(spec.iterations,
                if (!spec.iterationPeriods.isNegative) spec.iterationPeriods else Duration.ZERO,
                creationContext.createdStep!!))
        }
    }


}
