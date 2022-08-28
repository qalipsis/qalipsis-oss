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
import io.qalipsis.api.exceptions.InvalidSpecificationException
import java.time.Duration

/**
 * Specification for a [io.qalipsis.core.factory.steps.DelayedStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class DelayStepSpecification<INPUT>(
    @PositiveDuration val duration: Duration
) : AbstractStepSpecification<INPUT, INPUT, DelayStepSpecification<INPUT>>()

/**
 * Delays the workflow with the specified duration in milliseconds.
 *
 * @param duration the delay to set between the previous and next step, in millisecond.
 */
fun <INPUT> StepSpecification<*, INPUT, *>.delay(duration: Long): DelayStepSpecification<INPUT> {
    return delay(Duration.ofMillis(duration))
}

/**
 * Delays the workflow with the specified duration.
 *
 * @param duration the delay to set between the previous and next step.
 */
fun <INPUT> StepSpecification<*, INPUT, *>.delay(duration: Duration): DelayStepSpecification<INPUT> {
    if (duration.isZero || duration.isNegative) {
        throw InvalidSpecificationException("The delay should be at least 1 ms long")
    }
    val step = DelayStepSpecification<INPUT>(duration)
    this.add(step)
    return step
}
