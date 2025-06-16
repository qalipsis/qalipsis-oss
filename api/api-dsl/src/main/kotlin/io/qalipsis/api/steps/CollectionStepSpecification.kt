/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.api.steps

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.constraints.PositiveDuration
import io.qalipsis.api.lang.isLongerThan
import java.time.Duration
import javax.validation.constraints.Positive

/**
 * Specification for a [io.qalipsis.core.factory.steps.CollectionStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class CollectionStepSpecification<INPUT>(
    @field:PositiveDuration val batchTimeout: Duration?,
    @field:Positive val batchSize: Int
) :
    AbstractStepSpecification<INPUT, List<INPUT>, CollectionStepSpecification<INPUT>>()

/**
 * Consumes the input data coming from all the minions, and releases them as a list,
 * once either the linger timeout or the full capacity of the batch is reached.
 *
 * @param timeout linger duration between two releases, defaults to no timeout
 * @param batchSize the maximal size of the batch to trigger to release, defaults to no limit
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.collect(
    timeout: Duration? = null,
    batchSize: Int = 0
): CollectionStepSpecification<INPUT> {
    if (timeout?.isLongerThan(0) != true && batchSize <= 0) {
        throw IllegalArgumentException("Either the timeout or the batch size or both should be set.")
    }
    val step = CollectionStepSpecification<INPUT>(timeout, batchSize)
    this.add(step)
    return step
}
