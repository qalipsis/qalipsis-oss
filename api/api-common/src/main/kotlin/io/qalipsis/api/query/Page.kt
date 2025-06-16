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
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Page of records.
 *
 * @property page index of the current page
 * @property totalPages total count of pages matching the criteria
 * @property totalElements total count of elements matching the criteria
 * @property elements list of elements in the current page
 *
 * @author Palina Bril
 */
@Introspected
@Schema(
    name = "Page",
    title = "Page of the result",
    description = "Details of a page of records"
)
data class Page<T>(
    @field:Schema(description = "The index of the current page")
    val page: Int,

    @field:Schema(description = "The total count of pages matching the criteria")
    val totalPages: Int,

    @field:Schema(description = "The total count of elements matching the criteria")
    val totalElements: Long,

    @field:Schema(description = "The list of elements in the current page")
    val elements: List<T>
)