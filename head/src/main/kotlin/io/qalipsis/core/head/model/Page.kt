package io.qalipsis.core.head.model

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.Valid
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.PositiveOrZero

/**
 * Details of a Page to return by REST.
 *
 * @author Palina Bril
 */
@Introspected
@Schema(
    name = "Page",
    title = "Page of the result",
    description = "Details of a page with results from repository"
)
internal data class Page<T>(
    @field:Schema(description = "The index of the current page, default 0")
    @field:NotEmpty
    @field:PositiveOrZero
    val page: Int,

    @field:Schema(description = "The total count of pages matching the criteria")
    @field:NotEmpty
    @field:PositiveOrZero
    val totalPages: Int,

    @field:Schema(description = "The total number of elements matching the criteria (ex: 1000)")
    @field:NotEmpty
    @field:PositiveOrZero
    val totalElements: Long,

    @field:Schema(description = "The list of Campaigns of the current page(no more items than the size of the page)")
    val elements: List<@Valid T>
)