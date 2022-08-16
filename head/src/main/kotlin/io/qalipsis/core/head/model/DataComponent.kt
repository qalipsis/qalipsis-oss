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
    name = "Data component",
    title = "Data component to include in a report"
)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = Diagram::class, name = "DIAGRAM"),
    JsonSubTypes.Type(value = DataTable::class, name = "DATA_TABLE")
)
internal interface DataComponent {
    val type: DataComponentType
}

/**
 * @author Joël Valère
 */
@Introspected
@Schema(
    name = "A diagram",
    title = "A concrete data component to draw a diagram"
)
internal data class Diagram(
    var datas: List<DataSeries> = emptyList()
) : DataComponent {

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
    name = "A table of time-series records",
    title = "A concrete data component to display tabular time-series data"
)
internal data class DataTable(
    var datas: List<DataSeries> = emptyList()
) : DataComponent {

    override val type: DataComponentType = TYPE

    companion object {
        val TYPE = DataComponentType.DATA_TABLE
    }
}

/**
 * The type of data component to include in the report.
 *
 * @author Joël Valère
 */
@Introspected
internal enum class DataComponentType {
    DIAGRAM, DATA_TABLE
}
