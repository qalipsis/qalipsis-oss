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
interface DataComponent {
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
data class Diagram(
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
data class DataTable(
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
enum class DataComponentType {
    DIAGRAM, DATA_TABLE
}
