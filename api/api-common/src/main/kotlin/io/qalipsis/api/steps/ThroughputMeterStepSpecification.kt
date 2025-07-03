package io.qalipsis.api.steps

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.meters.Throughput
import io.qalipsis.api.meters.ThroughputFailureConditionSpec
import io.qalipsis.api.meters.ThroughputFailureConditionSpecImpl
import io.qalipsis.api.meters.specification.ComparableValueFailureSpecification
import java.time.Duration

/**
 * @TODO
 * Specification for a [io.qalipsis.core.factory.steps.TimerStep].
 *
 * @author Francisca Eze
 */
@Introspected
data class ThroughputMeterStepSpecification<INPUT>(
    val meterName: String,
    val percentiles: Map<Double, Duration>,
    val block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Double,
) : AbstractStepSpecification<INPUT, INPUT, ThroughputMeterStepSpecification<INPUT>>() {
    lateinit var checks: MutableList<ComparableValueFailureSpecification<Throughput, Double>>

    fun shouldFailWhen(block: ThroughputFailureConditionSpec.() -> INPUT): StepSpecification<*, INPUT, *> {
        val throughputFailureConditionSpec = ThroughputFailureConditionSpecImpl()
        throughputFailureConditionSpec.block()
        checks = throughputFailureConditionSpec.checks
        return this
    }

}

fun <INPUT> StepSpecification<*, INPUT, *>.throughput(
    name: String,
    percentiles: Map<Double, Duration> = emptyMap(),
    block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Double
): ThroughputMeterStepSpecification<INPUT> {
    val step = ThroughputMeterStepSpecification(name, percentiles, block)
    this.add(step)
    return step
}