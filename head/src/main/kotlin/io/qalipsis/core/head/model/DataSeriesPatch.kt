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
    JsonSubTypes.Type(value = TimeframeUnitDataSeriesPatch::class, name = TimeframeUnitDataSeriesPatch.TYPE)
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
        TimeframeUnitDataSeriesPatch::class
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
        const val TYPE = "displayName"
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
        const val TYPE = "sharingMode"
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
        const val TYPE = "fieldName"
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
        const val TYPE = "aggregationOperation"
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
        const val TYPE = "timeframeUnit"
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