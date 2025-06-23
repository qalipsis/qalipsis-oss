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
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.meters.meterConditions.TimerFailureConditionSpec
import io.qalipsis.api.meters.meterConditions.FailureSpecification
import io.qalipsis.api.meters.meterConditions.TimerFailureConditionSpecImpl
import java.time.Duration

/**
 * @TODO
 * Specification for a [io.qalipsis.core.factory.steps.TimerStep].
 *
 * @author Francisca Eze
 */
@Introspected
data class TimerMeterStepSpecification<INPUT>(
    val meterName: String,
    val percentiles: Map<Double, Duration>,
    val block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Duration,
) : AbstractStepSpecification<INPUT, INPUT, TimerMeterStepSpecification<INPUT>>() {
//    lateinit var checks: MutableList<FailureSpecification<Duration>>
    lateinit var checks: MutableList<FailureSpecification<Duration>>

    fun shouldFailWhen(block: TimerFailureConditionSpec.() -> INPUT): StepSpecification<*, INPUT, *> {
        val timerFailureConditionSpecImpl = TimerFailureConditionSpecImpl()
        timerFailureConditionSpecImpl.block()
        checks = timerFailureConditionSpecImpl.checks
        println("CHECKS53 $checks")
        return this
    }

}

fun <INPUT> StepSpecification<*, INPUT, *>.timer(
    name: String,
    percentiles: Map<Double, Duration> = emptyMap(),
    block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Duration
): TimerMeterStepSpecification<INPUT> {
    //extract duration from context and input
    val step = TimerMeterStepSpecification(name, percentiles, block)
    this.add(step)
    return step
}