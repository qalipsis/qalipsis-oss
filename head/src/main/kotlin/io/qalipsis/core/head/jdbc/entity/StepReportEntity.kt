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

package io.qalipsis.core.head.jdbc.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.model.naming.NamingStrategies
import javax.validation.constraints.NotBlank
import javax.validation.constraints.PositiveOrZero
import javax.validation.constraints.Size

/**
 * Entity encapsulating execution details of a single step within a scenario report.
 *
 * @author Eric Jessé
 */
@MappedEntity("step_report", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
data class StepReportEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    val id: Long,

    val scenarioReportId: Long,

    @field:NotBlank
    @field:Size(min = 1, max = 255)
    val name: String,

    @field:NotBlank
    @field:Size(min = 1, max = 255)
    val dagId: String,

    val isUnderLoad: Boolean,

    val initialized: Boolean,

    val initializationError: String? = null,

    @field:PositiveOrZero
    val successfulExecutions: Long = 0L,

    @field:PositiveOrZero
    val failedExecutions: Long = 0L
) {

    constructor(
        scenarioReportId: Long,
        name: String,
        dagId: String,
        isUnderLoad: Boolean,
        initialized: Boolean,
        initializationError: String? = null,
        successfulExecutions: Long = 0L,
        failedExecutions: Long = 0L
    ) : this(
        -1,
        scenarioReportId,
        name,
        dagId,
        isUnderLoad,
        initialized,
        initializationError,
        successfulExecutions,
        failedExecutions
    )
}
