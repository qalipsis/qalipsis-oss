package io.qalipsis.api.constraints

import javax.validation.Constraint

/**
 * Constraint to validate that a [java.time.Duration] is strictly positive.
 *
 * @author Eric Jessé
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [])
annotation class PositiveDuration(
    val message: String = "duration should be strictly positive but was {validatedValue}"
)