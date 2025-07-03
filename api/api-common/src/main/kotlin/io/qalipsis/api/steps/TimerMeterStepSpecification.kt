package io.qalipsis.api.steps

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.meters.Timer
import io.qalipsis.api.meters.specification.ComparableValueFailureSpecification
import io.qalipsis.api.meters.TimerFailureConditionSpec
import io.qalipsis.api.meters.TimerFailureConditionSpecImpl
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
    lateinit var checks: MutableList<ComparableValueFailureSpecification<Timer, Duration>>

    fun shouldFailWhen(block: TimerFailureConditionSpec.() -> INPUT): StepSpecification<*, INPUT, *> {
        val timerFailureConditionSpecImpl = TimerFailureConditionSpecImpl()
        timerFailureConditionSpecImpl.block()
        checks = timerFailureConditionSpecImpl.checks
        return this
    }

}

fun <INPUT> StepSpecification<*, INPUT, *>.timer(
    name: String,
    percentiles: Map<Double, Duration> = emptyMap(),
    block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Duration
): TimerMeterStepSpecification<INPUT> {
    val step = TimerMeterStepSpecification(name, percentiles, block)
    this.add(step)
    return step
}