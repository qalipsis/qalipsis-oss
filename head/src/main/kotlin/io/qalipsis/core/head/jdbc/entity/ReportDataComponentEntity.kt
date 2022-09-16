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

package io.qalipsis.core.head.jdbc.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.Relation.Cascade
import io.micronaut.data.jdbc.annotation.JoinColumn
import io.micronaut.data.jdbc.annotation.JoinTable
import io.micronaut.data.model.naming.NamingStrategies
import io.qalipsis.core.head.model.DataComponentType
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull


/**
 * Data component to include in the report.
 *
 * @property type the type of data component
 * @property dataSeries list of data series of the data component
 *
 * @author Joël Valère
 */
@MappedEntity("data_component", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
internal data class ReportDataComponentEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,

    @field:NotBlank
    val type: DataComponentType,

    @field:NotNull
    val reportId: Long,

    @field:Relation(value = Relation.Kind.MANY_TO_MANY, cascade = [Cascade.PERSIST])
    @field:JoinTable(
        name = "data_component_data_series",
        joinColumns = [JoinColumn(name = "data_component_id")],
        inverseJoinColumns = [JoinColumn(name = "data_series_id")]
    )
    val dataSeries: List<@Valid DataSeriesEntity>
) : Entity{
    constructor(
        reportId: Long,
        type: DataComponentType,
        dataSeries: List<DataSeriesEntity>
    ): this(
        id = -1,
        reportId = reportId,
        type = type,
        dataSeries = dataSeries
    )
}