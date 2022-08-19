package io.qalipsis.api.constraints

import javax.validation.Constraint

/**
 * Constraint to validate that a [java.time.Duration] is strictly positive.
 *
 * @author Eric Jessé
 */
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.TYPE,
    AnnotationTarget.TYPE_PARAMETER,
    AnnotationTarget.VALUE_PARAMETER
)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [PositiveDurationValidator::class])
annotation class PositiveDuration(
    val message: String = "duration should be strictly positive but was {validatedValue}"
)