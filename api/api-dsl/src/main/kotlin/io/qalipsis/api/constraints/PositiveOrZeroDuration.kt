package io.qalipsis.api.constraints

import javax.validation.Constraint

/**
 * Constraint to validate that a [java.time.Duration] is positive or zero.
 *
 * @author Eric Jessé
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [])
annotation class PositiveOrZeroDuration(
        val message: String = "duration should be positive or zero but was {validatedValue}"
)