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
import io.micronaut.data.model.naming.NamingStrategies
import io.qalipsis.core.head.model.ReportTask
import io.qalipsis.core.head.model.ReportTaskStatus
import java.time.Instant
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

/**
 * Persistence representation of a reportTask.
 *
 * @property id internal database ID
 * @property reportId internal database ID of the [ReportTaskEntity] being referenced
 * @property tenantReference reference of the tenant owning the task
 * @property status status of execution of file generation
 * @property creationTimestamp timestamp of creating task
 * @property updateTimestamp timestamp of updating task
 * @property creator username of the requester of the task creation
 *
 * @author Francisca Eze
 */
@MappedEntity("report_task", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
data class ReportTaskEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,

    @field:NotNull
    val reportId: Long,

    @field:NotBlank
    val tenantReference: String,

    @field:NotBlank
    val reference: String,

    @field:NotNull
    val status: ReportTaskStatus,

    val failureReason: String? = null,

    @field:NotBlank
    val creationTimestamp: Instant,

    @field:NotBlank
    val updateTimestamp: Instant,

    @field:NotBlank
    val creator: String,
) : Entity {
    constructor(
        reportId: Long,
        tenantReference: String,
        reference: String,
        status: ReportTaskStatus,
        failureReason: String? = null,
        creationTimestamp: Instant,
        updateTimestamp: Instant,
        creator: String
    ) : this(
        id = -1,
        reportId = reportId,
        tenantReference = tenantReference,
        reference = reference,
        status = status,
        failureReason = failureReason,
        creationTimestamp = creationTimestamp,
        updateTimestamp = updateTimestamp,
        creator = creator
    )

    fun toModel() = ReportTask(this)
}