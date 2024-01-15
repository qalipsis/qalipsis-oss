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
import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.naming.NamingStrategies
import io.qalipsis.core.head.report.SharingMode
import java.time.Instant
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.Size

/**
 * Persistable representation of a report.
 *
 * @property id internal database ID
 * @property reference unique public key
 * @property version version of the entity
 * @property tenantId internal database ID of the tenant in which the entity was created
 * @property creatorId internal database ID of the user who created the report entity
 * @property displayName display name of the time series, should be unique into a tenant
 * @property sharingMode sharing mode with the other members of the tenant
 * @property campaignKeys a list of campaign keys to be included in the report
 * @property campaignNamesPatterns a list of campaign names patterns to be included in the report
 * @property scenarioNamesPatterns a list of scenario names patterns to be included in the report
 * @property dataComponents a list of data components to include in the report
 * @property query prepared query to execute in the underlying report provider, it should be generated by the underlying report provider itself and saved as a valid JSON
 *
 * @author Joël Valère
 */
@MappedEntity("report", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
internal data class ReportEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,

    @field:NotBlank
    val reference: String,

    @field:Version
    val version: Instant,

    @field:NotNull
    val tenantId: Long,

    @field:NotNull
    val creatorId: Long,

    @field:NotBlank
    @field:Size(min = 1, max = 200)
    var displayName: String,

    var description: String? = null,

    @field:NotBlank
    var sharingMode: SharingMode = SharingMode.READONLY,

    @field:TypeDef(type = io.micronaut.data.model.DataType.JSON)
    val campaignKeys: Collection<String> = emptyList(),

    @field:TypeDef(type = io.micronaut.data.model.DataType.JSON)
    val campaignNamesPatterns: Collection<String> = emptyList(),

    @field:TypeDef(type = io.micronaut.data.model.DataType.JSON)
    val scenarioNamesPatterns: Collection<String> = emptyList(),

    @field:Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "reportId")
    val dataComponents: List<@Valid ReportDataComponentEntity>,

    var query: String?
) : Entity {

    constructor(
        reference: String,
        tenantId: Long,
        creatorId: Long,
        displayName: String,
        description: String? = null,
        sharingMode: SharingMode = SharingMode.READONLY,
        campaignKeys: Collection<String> = emptyList(),
        campaignNamesPatterns: Collection<String> = emptyList(),
        scenarioNamesPatterns: Collection<String> = emptyList(),
        dataComponents: List<ReportDataComponentEntity> = emptyList(),
        query: String? = null
    ) : this(
        id = -1,
        reference = reference,
        version = Instant.EPOCH,
        tenantId = tenantId,
        creatorId = creatorId,
        displayName = displayName,
        description = description,
        sharingMode = sharingMode,
        campaignKeys = campaignKeys,
        campaignNamesPatterns = campaignNamesPatterns,
        scenarioNamesPatterns = scenarioNamesPatterns,
        dataComponents = dataComponents,
        query = query,
    )
}