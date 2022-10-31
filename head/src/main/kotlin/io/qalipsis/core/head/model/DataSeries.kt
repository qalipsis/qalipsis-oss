/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.core.head.model

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.constraints.PositiveDuration
import io.qalipsis.api.query.QueryAggregationOperator
import io.qalipsis.api.query.QueryClauseOperator
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.DataSeriesFilterEntity
import io.qalipsis.core.head.report.DataType
import io.qalipsis.core.head.report.SharingMode
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Duration
import java.time.Instant
import javax.validation.Valid
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Pattern
import javax.validation.constraints.Size

/**
 * External representation of a data series.
 *
 * @author Palina Bril
 */
@Introspected
@Schema(
    name = "Data series details",
    title = "Details of a data series"
)
internal data class DataSeries(
    @field:Schema(description = "Identifier of the data series", required = true)
    val reference: String,

    @field:Schema(description = "Last update of the data series", required = true)
    val version: Instant,

    @field:Schema(description = "Name of the user, who created the data series", required = true)
    val creator: String,

    @field:Schema(description = "Display name of the time series, should be unique into a tenant", required = true)
    @field:NotBlank
    @field:Size(min = 3, max = 200)
    val displayName: String,

    @field:Schema(description = "Sharing mode with the other members of the tenant", required = false)
    val sharingMode: SharingMode = SharingMode.READONLY,

    @field:Schema(
        description = "Nature of data to fetch, corresponding to the different data types generated by tests",
        required = true
    )
    val dataType: DataType,

    @field:Schema(description = "Name of the event or meter to use", required = true)
    @field:NotBlank
    @field:Size(min = 3, max = 100)
    val valueName: String,

    @field:Schema(description = "Optional color to set, as an hexadecimal value", required = false)
    @field:Pattern(regexp = "^#[0-9a-fA-F]{6}$")
    val color: String?,

    @field:Schema(
        description = "Set of filters to apply to restrict the records eligible for the data series",
        required = false
    )
    val filters: Set<@Valid DataSeriesFilter>,

    @field:Schema(
        description = "Optional name of the field to use from the event or meter (ex: duration, count)",
        required = false
    )
    @field:Size(max = 60)
    val fieldName: String?,

    @field:Schema(
        description = "Aggregation operation to perform on the values pointed out by the field name,\n" +
                " * in order to scale the values with the time, defaults to count", required = false
    )
    val aggregationOperation: QueryAggregationOperator?,

    @field:PositiveDuration
    @field:Schema(
        description = "Duration to aggregate the values for scaling, as an ISO period (ex: PT10S), let empty for auto-mode",
        required = false
    )
    val timeframeUnit: Duration?,

    @field:Schema(
        description = "Optional display format to display the value, depending on its types",
        required = false
    )
    @field:Size(max = 20)
    val displayFormat: String?,

    @field:Schema(
        description = "The opacity of the color",
        required = false
    )
    @field:Min(value = 1)
    @field:Max(value = 100)
    val colorOpacity : Int? = null
) {

    constructor(
        displayName: String,
        sharingMode: SharingMode = SharingMode.READONLY,
        dataType: DataType,
        valueName: String,
        color: String? = null,
        filters: Set<DataSeriesFilter> = emptySet(),
        fieldName: String? = null,
        aggregationOperation: QueryAggregationOperator? = null,
        timeframeUnit: Duration? = null,
        displayFormat: String? = null,
        colorOpacity: Int? = null
    ) : this(
        reference = "",
        version = Instant.EPOCH,
        creator = "",
        displayName = displayName,
        sharingMode = sharingMode,
        dataType = dataType,
        valueName = valueName,
        color = color,
        filters = filters,
        fieldName = fieldName,
        aggregationOperation = aggregationOperation,
        timeframeUnit = timeframeUnit,
        displayFormat = displayFormat,
        colorOpacity = colorOpacity
    )

    constructor(dataSeriesEntity: DataSeriesEntity, creatorUsername: String) : this(
        reference = dataSeriesEntity.reference,
        version = dataSeriesEntity.version,
        creator = creatorUsername,
        displayName = dataSeriesEntity.displayName,
        sharingMode = dataSeriesEntity.sharingMode,
        dataType = dataSeriesEntity.dataType,
        valueName = dataSeriesEntity.valueName,
        color = dataSeriesEntity.color,
        filters = dataSeriesEntity.filters.map { it.toModel() }.toSet(),
        fieldName = dataSeriesEntity.fieldName,
        aggregationOperation = dataSeriesEntity.aggregationOperation,
        timeframeUnit = dataSeriesEntity.timeframeUnitAsDuration,
        displayFormat = dataSeriesEntity.displayFormat,
        colorOpacity = dataSeriesEntity.colorOpacity
    )
}

/**
 * External representation of a filter.
 *
 * @author Palina Bril
 */
@Introspected
@Schema(
    name = "Filter details",
    title = "Details of a filter to restrict the eligible data for a data series"
)
internal data class DataSeriesFilter(
    @field:Schema(description = "Name of the field to apply the filter", required = true)
    @field:NotBlank
    @field:Size(min = 1, max = 60)
    val name: String,

    @field:Schema(description = "Operator to perform on the values to filter records", required = true)
    val operator: QueryClauseOperator,

    @field:Schema(description = "Can contain wildcards signs * if the operator supports it", required = true)
    @field:NotBlank
    @field:Size(min = 1, max = 200)
    val value: String
) {
    fun toEntity(): DataSeriesFilterEntity {
        return DataSeriesFilterEntity(
            name = name,
            operator = operator,
            value = value
        )
    }
}