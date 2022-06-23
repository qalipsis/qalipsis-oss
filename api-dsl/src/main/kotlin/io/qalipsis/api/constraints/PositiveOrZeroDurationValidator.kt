package io.qalipsis.api.constraints

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.validation.validator.constraints.ConstraintValidator
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext
import jakarta.inject.Singleton
import java.time.Duration

/**
 * Validator for [PositiveOrZeroDuration] annotated elements.
 *
 * @author Eric Jessé
 */
@Singleton
internal class PositiveOrZeroDurationValidator : ConstraintValidator<PositiveOrZeroDuration, Duration> {

    override fun isValid(
        value: Duration?, annotationMetadata: AnnotationValue<PositiveOrZeroDuration>,
        context: ConstraintValidatorContext
    ): Boolean {
        return value == null || !value.isNegative
    }
}