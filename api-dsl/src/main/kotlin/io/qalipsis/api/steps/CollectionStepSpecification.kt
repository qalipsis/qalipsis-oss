/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
