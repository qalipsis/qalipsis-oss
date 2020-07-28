package io.evolue.core.factory.steps.converters

import io.evolue.api.annotations.StepConverter
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.steps.AbstractStepSpecification
import io.evolue.api.steps.BlackHoleStepSpecification
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
@StepConverter
internal class IterativeStepDecoratorSpecificationConverter :
    StepSpecificationDecoratorConverter<StepSpecification<*, *, *>>() {

    override val order: Int = 750

    override suspend fun decorate(creationContext: StepCreationContext<StepSpecification<*, *, *>>) {
        val spec = creationContext.stepSpecification
        if (spec.iterations > 1) {
            val iterativeStep = IterativeStepDecorator(spec.iterations,
                if (!spec.iterationPeriods.isNegative) spec.iterationPeriods else Duration.ZERO,
                creationContext.createdStep!!)
            if (spec.nextSteps.isEmpty()) {
                log.trace("Adding a black hole step to the iterative step with no next")
                // Add a black hole in order to consume the output and make sure all the iterations can be performed.
                spec.add(BlackHoleStepSpecification<Any?>())
            }
            creationContext.createdStep(iterativeStep)
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }

}
