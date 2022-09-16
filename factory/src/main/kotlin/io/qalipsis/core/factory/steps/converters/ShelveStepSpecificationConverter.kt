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
import io.qalipsis.api.states.SharedStateRegistry
import io.qalipsis.api.steps.ShelveStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factory.steps.ShelveStep

/**
 * [StepSpecificationConverter] from [ShelveStepSpecification] to [ShelveStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class ShelveStepSpecificationConverter(
    private val sharedStateRegistry: SharedStateRegistry
) : StepSpecificationConverter<ShelveStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is ShelveStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<ShelveStepSpecification<*>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as ShelveStepSpecification<I>
        val step = ShelveStep(spec.name, sharedStateRegistry, spec.specification)
        creationContext.createdStep(step)
    }

}
