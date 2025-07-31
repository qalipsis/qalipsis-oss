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

import io.micronaut.core.annotation.Introspected
import io.qalipsis.core.head.jdbc.entity.ReportTaskEntity
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/**
 * External representation of a report task.
 *
 * @author Francisca Eze
 */
@Introspected
@Schema(
    name = "Report task details",
    title = "Details of a report task"
)
data class ReportTask(

    @field:Schema(description = "Identifier of a report task", required = true)
    val reference: String,

    @field:Schema(description = "Status of report task generation", required = true)
    val status: ReportTaskStatus,

    @field:Schema(description = "The cause of a task generation failure", required = false)
    val failureReason: String? = null,

    @field:Schema(description = "Starting time of task generation", required = true)
    val creationTimestamp: Instant,

    @field:Schema(description = "Update time of task generation", required = false)
    val updateTimestamp: Instant?,

    @field:Schema(description = "Creator of the report task", required = true)
    val creator: String,
) {
    constructor(
        reference: String,
        status: ReportTaskStatus,
        failureReason: String?,
        creator: String
    ) : this(
        reference = reference,
        status = status,
        failureReason = failureReason,
        creationTimestamp = Instant.now(),
        updateTimestamp = null,
        creator = creator
    )

    constructor(reportTaskEntity: ReportTaskEntity) : this(
        reference = reportTaskEntity.reference,
        status = reportTaskEntity.status,
        failureReason = reportTaskEntity.failureReason,
        creationTimestamp = reportTaskEntity.creationTimestamp,
        updateTimestamp = reportTaskEntity.updateTimestamp,
        creator = reportTaskEntity.creator
    )

}