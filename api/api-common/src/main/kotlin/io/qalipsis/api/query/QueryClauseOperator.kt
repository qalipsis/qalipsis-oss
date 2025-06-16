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

package io.qalipsis.api.query

import io.micronaut.core.annotation.Introspected

/**
 * Operator to apply on a clause for a query.
 *
 * @author Eric Jessé
 */
@Introspected
enum class QueryClauseOperator {
    IS, IS_NOT, IS_IN, IS_NOT_IN, IS_LIKE, IS_NOT_LIKE, IS_GREATER_THAN, IS_LOWER_THAN, IS_GREATER_OR_EQUAL_TO, IS_LOWER_OR_EQUAL_TO
}