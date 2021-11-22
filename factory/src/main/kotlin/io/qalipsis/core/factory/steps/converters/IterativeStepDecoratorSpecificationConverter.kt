package io.qalipsis.core.factory.steps.converters

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.BlackHoleStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationDecoratorConverter
import io.qalipsis.core.factory.steps.IterativeStepDecorator
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
                log.trace { "Adding a black hole step to the iterative step with no next" }
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
