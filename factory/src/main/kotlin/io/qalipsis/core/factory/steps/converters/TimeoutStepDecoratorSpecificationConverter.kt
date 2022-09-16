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

import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationDecoratorConverter
import io.qalipsis.core.factory.steps.TimeoutStepDecorator

/**
 * [StepSpecificationDecoratorConverter] from any [AbstractStepSpecification] to [TimeoutStepDecorator].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class TimeoutStepDecoratorSpecificationConverter(
    private val meterRegistry: MeterRegistry
) : StepSpecificationDecoratorConverter<StepSpecification<*, *, *>>() {

    override val order: Int = 500

    override suspend fun decorate(creationContext: StepCreationContext<StepSpecification<*, *, *>>) {
        val spec = creationContext.stepSpecification
        if (spec.timeout != null) {
            creationContext.createdStep(
                TimeoutStepDecorator(spec.timeout!!, creationContext.createdStep!!, meterRegistry))
        }
    }

}
