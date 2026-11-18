/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
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
    name = "Data component for creation or update of a report",
    title = "Details of data component to include in a report for creation or update of a report"
)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DiagramCreationAndUpdateRequest::class, name = "DIAGRAM"),
    JsonSubTypes.Type(value = DataTableCreationAndUpdateRequest::class, name = "DATA_TABLE")
)
interface DataComponentCreationAndUpdateRequest {
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
data class DiagramCreationAndUpdateRequest(
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
data class DataTableCreationAndUpdateRequest(
    var dataSeriesReferences: List<String> = emptyList()
) : DataComponentCreationAndUpdateRequest {

    override val type: DataComponentType = TYPE

    companion object {
        val TYPE = DataComponentType.DATA_TABLE
    }
}