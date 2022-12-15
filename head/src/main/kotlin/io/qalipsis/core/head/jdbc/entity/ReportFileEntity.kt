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
import java.time.Instant
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

/**
 * Persistence representation of a reportFile.
 *
 * @property id internal database ID
 * @property fileContent report file content
 * @property creationTimestamp timestamp of creation
 * @property reportTaskId internal database ID of the [ReportTaskEntity] that generated the file
 *
 * @author Francisca Eze
 */
@MappedEntity("report_file", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
internal data class ReportFileEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,

    @field:NotBlank
    val name: String,

    @field:NotNull
    val fileContent: ByteArray,

    @field:NotBlank
    val creationTimestamp: Instant,

    val reportTaskId: Long
) : Entity {
    constructor(
        name: String,
        fileContent: ByteArray,
        creationTimestamp: Instant,
        reportTaskId: Long
    ) : this(
        id = -1,
        name = name,
        fileContent = fileContent,
        creationTimestamp = creationTimestamp,
        reportTaskId = reportTaskId
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReportFileEntity

        if (id != other.id) return false
        if (!fileContent.contentEquals(other.fileContent)) return false
        if (creationTimestamp != other.creationTimestamp) return false
        return reportTaskId == other.reportTaskId
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + fileContent.contentHashCode()
        result = 31 * result + creationTimestamp.hashCode()
        result = 31 * result + reportTaskId.hashCode()
        return result
    }
}