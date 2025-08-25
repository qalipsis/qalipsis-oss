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

package io.qalipsis.api.meters.steps.failure

/**
 * Defines the failure condition specification for rate-based meters. It defines
 * how the current value of a [Rate] should be evaluated against a threshold
 * or range-based conditions.
 *
 * @author Francisca Eze
 */
interface RateFailureConditionSpec {

    /**
     * Allows evaluation of failure conditions on the current value property.
     */
    val current: FailureSpecification<Double>
}