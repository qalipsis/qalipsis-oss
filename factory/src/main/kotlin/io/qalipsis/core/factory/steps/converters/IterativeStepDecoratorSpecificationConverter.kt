/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

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
class IterativeStepDecoratorSpecificationConverter :
    StepSpecificationDecoratorConverter<StepSpecification<*, *, *>>() {

    override val order: Int = 750

    override suspend fun decorate(creationContext: StepCreationContext<StepSpecification<*, *, *>>) {
        val spec = creationContext.stepSpecification
        if (spec.iterations > 1) {
            val iterativeStep = IterativeStepDecorator(
                iterations = spec.iterations,
                delay = if (!spec.iterationPeriods.isNegative) spec.iterationPeriods else Duration.ZERO,
                stopOnError = spec.stopIterationsOnError,
                decorated = creationContext.createdStep!!
            )
            if (spec.nextSteps.isEmpty()) {
                log.trace { "Adding a black hole step to the iterative step with no next" }
                // Add a black hole in order to consume the output and make sure all the iterations can be performed.
                spec.add(BlackHoleStepSpecification<Any?>().apply { name = "__" })
            }
            creationContext.createdStep(iterativeStep)
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }

}
