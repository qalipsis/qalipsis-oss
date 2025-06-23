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

package io.qalipsis.api.meters.steps

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.meters.Timer
import io.qalipsis.api.meters.steps.failure.TimerFailureConditionSpec
import io.qalipsis.api.meters.steps.failure.impl.ComparableValueFailureSpecification
import io.qalipsis.api.meters.steps.failure.impl.TimerFailureConditionSpecImpl
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepSpecification
import java.time.Duration

/**
 * Specification for a [io.qalipsis.core.factory.steps.meter.TimerMeterStep] to allow for configuration of failure conditions.
 *
 * @author Francisca Eze
 */
interface TimerMeterStepSpecification<INPUT> :
    StepSpecification<INPUT, INPUT, TimerMeterStepSpecification<INPUT>>,
    ConfigurableStepSpecification<INPUT, INPUT, TimerMeterStepSpecification<INPUT>> {

    /**
     * Allows specification of failure conditions on the timer meter.
     */
    fun shouldFailWhen(block: TimerFailureConditionSpec.() -> Unit): ConfigurableStepSpecification<INPUT, INPUT, *>
}

/**
 * Specification for a [TimerMeterStepSpecification].
 *
 * @author Francisca Eze
 */
@Spec
data class TimerMeterStepSpecificationImpl<INPUT>(
    val meterName: String,
    val block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Duration,
) : TimerMeterStepSpecification<INPUT>,
    AbstractStepSpecification<INPUT, INPUT, TimerMeterStepSpecification<INPUT>>() {

    var checks: MutableList<ComparableValueFailureSpecification<Timer, Duration>> = mutableListOf()

    val percentiles: MutableSet<Double> = mutableSetOf()

    override fun shouldFailWhen(block: TimerFailureConditionSpec.() -> Unit): ConfigurableStepSpecification<INPUT, INPUT, *> {
        val timerFailureConditionSpecImpl = TimerFailureConditionSpecImpl(percentiles)
        timerFailureConditionSpecImpl.block()
        checks = timerFailureConditionSpecImpl.checks
        return this
    }

}

/**
 * Executes the block on the context to extract a value to be recorded in the meter.
 *
 * @param name the name of the meter.
 * @param block the rule to convert the input and the context into the output.
 *
 * @author Francisca Eze
 */
fun <INPUT> StepSpecification<*, INPUT, *>.timer(
    name: String,
    block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Duration
): TimerMeterStepSpecification<INPUT> {
    val step = TimerMeterStepSpecificationImpl(name, block)
    this.add(step)
    return step
}