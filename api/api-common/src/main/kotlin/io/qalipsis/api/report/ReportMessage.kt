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

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.context.StepName
import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.constraints.NotBlank

/**
 * Message for a [ScenarioReport].
 *
 * @author Eric Jessé
 */
@Introspected
@Schema(
    name = "Details about report message of a completed scenario",
    title = "Details for the scenario report message to retrieve from the REST endpoint"
)
data class ReportMessage(
    @field:Schema(description = "Identifier of the step")
    @field:NotBlank
    val stepName: StepName,

    @field:Schema(description = "Identifier of the message")
    @field:NotBlank
    val messageId: String,

    @field:Schema(description = "Severity of the report message")
    val severity: ReportMessageSeverity,

    @field:Schema(description = "The message itself")
    val message: String
)
