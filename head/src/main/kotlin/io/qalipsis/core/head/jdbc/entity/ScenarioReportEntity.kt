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
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.naming.NamingStrategies
import io.qalipsis.api.report.ExecutionStatus
import java.time.Instant
import javax.validation.constraints.NotBlank
import javax.validation.constraints.PositiveOrZero
import javax.validation.constraints.Size

/**
 * Entity to encapsulate data of scenario report table.
 *
 * @author Palina Bril
 */
@MappedEntity("scenario_report", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
internal data class ScenarioReportEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    @field:Version
    override val version: Instant,
    @field:NotBlank
    @field:Size(min = 1, max = 255)
    val name: String,
    val campaignReportId: Long,
    val start: Instant,
    val end: Instant,
    @field:PositiveOrZero
    val startedMinions: Int,
    @field:PositiveOrZero
    val completedMinions: Int,
    @field:PositiveOrZero
    val successfulExecutions: Int,
    @field:PositiveOrZero
    val failedExecutions: Int,
    val status: ExecutionStatus,
    @field:Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "scenarioReportId")
    val messages: List<ScenarioReportMessageEntity>
) : VersionedEntity {

    constructor(
        name: String,
        campaignReportId: Long,
        start: Instant = Instant.now(),
        end: Instant = Instant.now(),
        startedMinions: Int = 0,
        completedMinions: Int = 0,
        successfulExecutions: Int = 0,
        failedExecutions: Int = 0,
        status: ExecutionStatus,
        messages: List<ScenarioReportMessageEntity> = emptyList()
    ) : this(
        -1,
        Instant.EPOCH,
        name,
        campaignReportId,
        start,
        end,
        startedMinions,
        completedMinions,
        successfulExecutions,
        failedExecutions,
        status,
        messages
    )
}