package io.evolue.api.constraints

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.validation.validator.constraints.ConstraintValidator
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext
import java.time.Duration

/**
 * Validator for [PositiveDuration] annotated elements.
 *
 * @author Eric Jessé
 */
class PositiveDurationValidator : ConstraintValidator<PositiveDuration, Duration> {

    override fun isValid(value: Duration?, annotationMetadata: AnnotationValue<PositiveDuration>,
                         context: ConstraintValidatorContext): Boolean {
        return value != null && !value.isZero && !value.isNegative
    }
}