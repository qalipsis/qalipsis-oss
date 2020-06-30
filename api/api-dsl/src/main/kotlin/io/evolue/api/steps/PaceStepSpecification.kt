package io.evolue.api.steps

import java.time.Duration

/**
 * Specification for a [io.evolue.core.factory.steps.PaceStep].
 *
 * @author Eric Jessé
 */
data class PaceStepSpecification<INPUT>(
    val specification: (pastPeriodMs: Long) -> Long
) : AbstractStepSpecification<INPUT, INPUT, PaceStepSpecification<INPUT>>()

fun <INPUT> StepSpecification<*, INPUT, *>.pace(
    specification: (pastPeriodMs: Long) -> Long): PaceStepSpecification<INPUT> {
    val step = PaceStepSpecification<INPUT>(specification)
    this.add(step)
    return step
}

fun <INPUT> StepSpecification<*, INPUT, *>.constantPace(durationInMs: Long): PaceStepSpecification<INPUT> {
    return pace { _ -> durationInMs }
}

fun <INPUT> StepSpecification<*, INPUT, *>.constantPace(duration: Duration): PaceStepSpecification<INPUT> {
    return pace { _ -> duration.toMillis() }
}

fun <INPUT> StepSpecification<*, INPUT, *>.acceleratingPace(startPeriodMs: Long, accelerator: Double,
                                                            minPeriodMs: Long): PaceStepSpecification<INPUT> {
    // Secure the accelerator to avoid divide by 0 and negative values.
    val actualAccelerator = 1 / accelerator.coerceAtLeast(10E-12)
    return pace { pastPeriodMs ->
        if (pastPeriodMs == 0L) {
            startPeriodMs
        } else {
            (pastPeriodMs * actualAccelerator).toLong().coerceAtLeast(minPeriodMs)
        }
    }
}
