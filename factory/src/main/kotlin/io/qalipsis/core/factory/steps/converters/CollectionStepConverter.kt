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

import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.steps.CollectionStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factory.steps.CollectionStep
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineScope

/**
 * [StepSpecificationConverter] from [CollectionStepSpecification] to [CollectionStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class CollectionStepSpecificationConverter(
    @Named(Executors.CAMPAIGN_EXECUTOR_NAME) private val coroutineScope: CoroutineScope
) : StepSpecificationConverter<CollectionStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is CollectionStepSpecification
    }

    override suspend fun <I, O> convert(
        creationContext: StepCreationContext<CollectionStepSpecification<*>>
    ) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as CollectionStepSpecification<I>
        val step = CollectionStep<I>(
            spec.name, spec.batchTimeout,
            if (spec.batchSize <= 0) Int.MAX_VALUE else spec.batchSize,
            coroutineScope
        )
        creationContext.createdStep(step)
    }

}
