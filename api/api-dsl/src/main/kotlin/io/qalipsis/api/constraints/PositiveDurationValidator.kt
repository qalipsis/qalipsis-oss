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

package io.qalipsis.api.constraints

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.validation.validator.constraints.ConstraintValidator
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext
import jakarta.inject.Singleton
import java.time.Duration

/**
 * Validator for [PositiveDuration] annotated elements.
 *
 * @author Eric Jessé
 */
@Singleton
class PositiveDurationValidator : ConstraintValidator<PositiveDuration, Duration> {

    override fun isValid(
        value: Duration?, annotationMetadata: AnnotationValue<PositiveDuration>,
        context: ConstraintValidatorContext
    ): Boolean {
        return value == null || (!value.isZero && !value.isNegative)
    }
}