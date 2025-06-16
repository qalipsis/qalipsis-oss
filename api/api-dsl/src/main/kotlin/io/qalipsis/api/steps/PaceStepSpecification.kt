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
import java.time.Duration

/**
 * Specification for a [io.qalipsis.core.factory.steps.PaceStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class PaceStepSpecification<INPUT>(
    val specification: (pastPeriodMs: Long) -> Long
) : AbstractStepSpecification<INPUT, INPUT, PaceStepSpecification<INPUT>>()

/**
 * Forces the records to be forwarded to the next step with the given pace. The pace calculation is isolated
 * for each minion.
 *
 * @param specification the calculation of the pace, considering the previous one (which is 0 at start).
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.pace(
    specification: (pastPeriodMs: Long) -> Long
): PaceStepSpecification<INPUT> {
    val step = PaceStepSpecification<INPUT>(specification)
    this.add(step)
    return step
}

/**
 * Forces the records to be forwarded to the next step with a constant pace. The pace calculation is isolated
 * for each minion.
 *
 * @param durationInMs the constant delay between two forwarding of record to the next step, in milliseconds.
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.constantPace(durationInMs: Long): PaceStepSpecification<INPUT> {
    return pace { _ -> durationInMs }
}

/**
 * Forces the records to be forwarded to the next step with a constant pace. The pace calculation is isolated
 * for each minion.
 *
 * @param duration the constant delay between two forwarding of record to the next step.
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.constantPace(duration: Duration): PaceStepSpecification<INPUT> {
    return pace { _ -> duration.toMillis() }
}

/**
 * Forces the records to be forwarded to the next step, accelerating the pace at each record. The pace calculation is isolated
 * for each minion.
 *
 * @param startPeriodMs the duration to apply between the first and the second record, in milliseconds.
 * @param accelerator the acceleration factor to divide the previous period: use a value greater than 1 to accelerate, between 0 and 1 to go lower.
 * @param minPeriodMs the minimal period limit in milliseconds.
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.acceleratingPace(
    startPeriodMs: Long, accelerator: Double,
    minPeriodMs: Long
): PaceStepSpecification<INPUT> {
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
