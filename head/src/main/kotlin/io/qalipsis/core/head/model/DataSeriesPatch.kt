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

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.constraints.PositiveDuration
import io.qalipsis.api.query.QueryAggregationOperator
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.report.SharingMode
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Duration
import javax.validation.Valid
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Pattern
import javax.validation.constraints.Size

/**
 * Interface that has several implementations, each one being in charge of changing only one aspect of a data series
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DisplayNameDataSeriesPatch::class, name = DisplayNameDataSeriesPatch.TYPE),
    JsonSubTypes.Type(value = ColorDataSeriesPatch::class, name = ColorDataSeriesPatch.TYPE),
    JsonSubTypes.Type(value = FieldNameDataSeriesPatch::class, name = FieldNameDataSeriesPatch.TYPE),
    JsonSubTypes.Type(value = DisplayFormatDataSeriesPatch::class, name = DisplayFormatDataSeriesPatch.TYPE),
    JsonSubTypes.Type(value = SharingModeDataSeriesPatch::class, name = SharingModeDataSeriesPatch.TYPE),
    JsonSubTypes.Type(value = FiltersDataSeriesPatch::class, name = FiltersDataSeriesPatch.TYPE),
    JsonSubTypes.Type(
        value = AggregationOperationDataSeriesPatch::class,
        name = AggregationOperationDataSeriesPatch.TYPE
    ),
    JsonSubTypes.Type(value = TimeframeUnitDataSeriesPatch::class, name = TimeframeUnitDataSeriesPatch.TYPE),
    JsonSubTypes.Type(value = ColorOpacityDataSeriesPatch::class, name = ColorOpacityDataSeriesPatch.TYPE)
)
@Introspected
@Schema(
    title = "Patch to update an existing data series",
    allOf = [
        DisplayNameDataSeriesPatch::class,
        ColorDataSeriesPatch::class,
        FieldNameDataSeriesPatch::class,
        DisplayFormatDataSeriesPatch::class,
        SharingModeDataSeriesPatch::class,
        FiltersDataSeriesPatch::class,
        AggregationOperationDataSeriesPatch::class,
        TimeframeUnitDataSeriesPatch::class,
        ColorOpacityDataSeriesPatch::class
    ]
)
internal interface DataSeriesPatch {
    /**
     * Applies a change on the [DataSeriesEntity] and returns true if and only if the change was actually
     * performed.
     */
    fun apply(dataSeries: DataSeriesEntity): Boolean = false

    val type: String
}

/**
 * Implementation of the [DataSeriesPatch] interface, that is in charge of changing displayName property of a data series
 */
@Introspected
@Schema(title = "Patch to update the display name of a data series")
internal class DisplayNameDataSeriesPatch(
    @field:NotBlank
    @field:Size(min = 3, max = 200)
    val displayName: String
) : DataSeriesPatch {

    override val type: String = TYPE

    override fun apply(dataSeries: DataSeriesEntity): Boolean {
        return if (dataSeries.displayName != displayName.trim()) {
            dataSeries.displayName = displayName.trim()
            true
        } else {
            false
        }
    }

    companion object {
        const val TYPE = "display-name"
    }
}

/**
 * Implementation of the [DataSeriesPatch] interface, that is in charge of changing sharingMode property of a data series
 */
@Introspected
@Schema(title = "Patch to update the sharing mode of a data series")
internal class SharingModeDataSeriesPatch(
    private val newSharingMode: SharingMode
) : DataSeriesPatch {

    override val type: String = TYPE

    override fun apply(dataSeries: DataSeriesEntity): Boolean {
        return if (dataSeries.sharingMode != newSharingMode) {
            dataSeries.sharingMode = newSharingMode
            true
        } else {
            false
        }
    }

    companion object {
        const val TYPE = "sharing-mode"
    }
}

/**
 * Implementation of the [DataSeriesPatch] interface, that is in charge of changing color property of a data series
 */
@Introspected
@Schema(title = "Patch to update the display color of a data series")
internal class ColorDataSeriesPatch(
    @field:Pattern(regexp = "^#[0-9a-fA-F]{6}$")
    val color: String?
) : DataSeriesPatch {

    override val type: String = TYPE

    override fun apply(dataSeries: DataSeriesEntity): Boolean {
        val newColor = color?.trim()?.uppercase()
        return if (dataSeries.color != newColor) {
            dataSeries.color = newColor
            true
        } else {
            false
        }
    }

    companion object {
        const val TYPE = "color"
    }
}

/**
 * Implementation of the [DataSeriesPatch] interface, that is in charge of changing filters property of a data series
 */
@Introspected
@Schema(title = "Patch to update the filters of a data series")
internal class FiltersDataSeriesPatch(
    val filters: Set<@Valid DataSeriesFilter>
) : DataSeriesPatch {

    override val type: String = TYPE

    override fun apply(dataSeries: DataSeriesEntity): Boolean {
        val entities = filters.map { it.toEntity() }.toSet()
        return if (dataSeries.filters != entities) {
            dataSeries.filters = entities
            true
        } else {
            false
        }
    }

    companion object {
        const val TYPE = "filters"
    }
}

/**
 * Implementation of the [DataSeriesPatch] interface, that is in charge of changing fieldName property of a data series
 */
@Introspected
@Schema(title = "Patch to update the field name of a data series")
internal class FieldNameDataSeriesPatch(
    @field:NotBlank
    @field:Size(max = 60)
    val fieldName: String?
) : DataSeriesPatch {

    override val type: String = TYPE

    override fun apply(dataSeries: DataSeriesEntity): Boolean {
        return if (dataSeries.fieldName != fieldName?.trim()) {
            dataSeries.fieldName = fieldName?.trim()
            true
        } else {
            false
        }
    }

    companion object {
        const val TYPE = "field-name"
    }
}

/**
 * Implementation of the [DataSeriesPatch] interface, that is in charge of changing fieldName property of a data series
 */
@Introspected
@Schema(title = "Patch to update the name of the used meter/event")
internal class ValueNameDataSeriesPatch(
    @field:NotBlank
    @field:Size(min = 3, max = 100)
    val valueName: String
) : DataSeriesPatch {

    override val type: String = TYPE

    override fun apply(dataSeries: DataSeriesEntity): Boolean {
        return if (dataSeries.valueName != valueName.trim()) {
            dataSeries.valueName = valueName.trim()
            true
        } else {
            false
        }
    }

    companion object {
        const val TYPE = "value-name"
    }
}

/**
 * Implementation of the [DataSeriesPatch] interface, that is in charge of changing aggregationOperation property of a data series
 */
@Introspected
@Schema(title = "Patch to update the aggregation operation of a data series")
internal class AggregationOperationDataSeriesPatch(
    private val aggregationOperation: QueryAggregationOperator
) : DataSeriesPatch {

    override val type: String = TYPE

    override fun apply(dataSeries: DataSeriesEntity): Boolean {
        return if (dataSeries.aggregationOperation != aggregationOperation) {
            dataSeries.aggregationOperation = aggregationOperation
            true
        } else {
            false
        }
    }

    companion object {
        const val TYPE = "aggregation"
    }
}

/**
 * Implementation of the [DataSeriesPatch] interface, that is in charge of changing timeframeUnit property of a data series
 */
@Introspected
@Schema(title = "Patch to update the duration to aggregate the values of a data series for scaling ")
internal class TimeframeUnitDataSeriesPatch(
    @field:PositiveDuration
    val timeframeUnit: Duration?
) : DataSeriesPatch {

    override val type: String = TYPE

    override fun apply(dataSeries: DataSeriesEntity): Boolean {
        return if (dataSeries.timeframeUnitAsDuration != timeframeUnit) {
            dataSeries.timeframeUnitMs = timeframeUnit?.toMillis()
            true
        } else {
            false
        }
    }

    companion object {
        const val TYPE = "timeframe"
    }
}

/**
 * Implementation of the [DataSeriesPatch] interface, that is in charge of changing displayFormat property of a data series
 */
@Introspected
internal class DisplayFormatDataSeriesPatch(
    @field:Size(max = 20)
    val displayFormat: String?
) : DataSeriesPatch {

    override val type: String = TYPE

    override fun apply(dataSeries: DataSeriesEntity): Boolean {
        return if (dataSeries.displayFormat != displayFormat?.trim()) {
            dataSeries.displayFormat = displayFormat?.trim()
            true
        } else {
            false
        }
    }

    companion object {
        const val TYPE = "displayFormat"
    }
}

/**
 * Implementation of the [DataSeriesPatch] interface, that is in charge of changing color opacity property of a data series
 */
@Introspected
internal class ColorOpacityDataSeriesPatch(
    @field:Min(value = 1)
    @field:Max(value = 100)
    val colorOpacity: Int?
) : DataSeriesPatch {

    override val type: String = TYPE

    override fun apply(dataSeries: DataSeriesEntity): Boolean {
        return if (dataSeries.colorOpacity != colorOpacity) {
            dataSeries.colorOpacity = colorOpacity
            true
        } else {
            false
        }
    }

    companion object {
        const val TYPE = "colorOpacity"
    }
}