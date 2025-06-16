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

package io.qalipsis.api.report

/**
 * Field of a time-series data source.
 *
 * @author Eric Jessé
 *
 * @property name name of the field in the data source
 * @property type type for the
 * @property unit the unit of the values, if relevant (durations,...) and not specified in the records
 */
data class DataField(
    val name: String,
    val type: DataFieldType,
    val unit: String? = null
)

/**
 * Type of a field of time-series data.
 */
enum class DataFieldType {
    STRING, NUMBER, BOOLEAN, OBJECT, DATE
}
