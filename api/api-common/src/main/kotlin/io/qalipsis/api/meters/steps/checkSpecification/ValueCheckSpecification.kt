/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.api.meters.steps.checkSpecification

/**
 * Represents a specification for checking a comparable value using a condition.
 *
 * @author Francisca Eze
 */
sealed interface ValueCheckSpecification<T> {
    val valueName: String
}

/**
 * Custom specification that allows for comparison of a value against a threshold.
 */
sealed interface SingleThresholdValueCheckSpecification<T> : ValueCheckSpecification<T> {
    val expected: T
}

/**
 * Custom specification that allows for comparison of a value against a range of values.
 */
sealed interface RangeValueCheckSpecification<T> : ValueCheckSpecification<T> {
    val lowerBound: T
    val upperBound: T
}