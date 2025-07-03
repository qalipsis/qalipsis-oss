package io.qalipsis.api.steps

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.CounterFailureConditionSpec
import io.qalipsis.api.meters.CounterFailureConditionSpecImpl
import io.qalipsis.api.meters.specification.ComparableValueFailureSpecification

/**
 * @TODO
 * Specification for a [io.qalipsis.core.factory.steps.TimerStep].
 *
 * @author Francisca Eze
 */
@Introspected
data class CounterMeterStepSpecification<INPUT>(
    val meterName: String,
    val block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Double,
) : AbstractStepSpecification<INPUT, INPUT, CounterMeterStepSpecification<INPUT>>() {
    lateinit var checks: MutableList<ComparableValueFailureSpecification<Counter, Double>>

    fun shouldFailWhen(block: CounterFailureConditionSpec.() -> INPUT): StepSpecification<*, INPUT, *> {
        val counterFailureConditionSpecImpl = CounterFailureConditionSpecImpl()
        counterFailureConditionSpecImpl.block()
        checks = counterFailureConditionSpecImpl.checks
        return this
    }

}

fun <INPUT> StepSpecification<*, INPUT, *>.counter(
    name: String,
    block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Double
): CounterMeterStepSpecification<INPUT> {
    val step = CounterMeterStepSpecification(name, block)
    this.add(step)
    return step
}