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