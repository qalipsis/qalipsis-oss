package io.qalipsis.core.head.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

/**
 * @author Joël Valère
 */
@Introspected
@Schema(
    name = "Data component for creation or update of a report",
    title = "Details of data component to include in a report for creation or update of a report"
)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DiagramCreationAndUpdateRequest::class, name = "DIAGRAM"),
    JsonSubTypes.Type(value = DataTableCreationAndUpdateRequest::class, name = "DATA_TABLE")
)
internal interface DataComponentCreationAndUpdateRequest{
    val type: DataComponentType
}

/**
 * @author Joël Valère
 */
@Introspected
@Schema(
    name = "A diagram",
    title = "Creation or update of a data component to draw a diagram"
)
internal data class DiagramCreationAndUpdateRequest(
    var dataSeriesReferences: List<String> = emptyList()
) : DataComponentCreationAndUpdateRequest {

    override val type: DataComponentType = TYPE

    companion object {
        val TYPE = DataComponentType.DIAGRAM
    }
}

/**
 * @author Joël Valère
 */
@Introspected
@Schema(
    name = "A table for time-series data",
    title = "Creation or update of a data component to display tabular time-series data"
)
internal data class DataTableCreationAndUpdateRequest(
    var dataSeriesReferences: List<String> = emptyList()
) : DataComponentCreationAndUpdateRequest {

    override val type: DataComponentType = TYPE

    companion object {
        val TYPE = DataComponentType.DATA_TABLE
    }
}