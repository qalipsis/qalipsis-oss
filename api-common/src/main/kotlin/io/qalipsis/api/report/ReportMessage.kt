/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
    val messageId: Any,

    @field:Schema(description = "Severity of the report message")
    val severity: ReportMessageSeverity,

    @field:Schema(description = "The message itself")
    val message: String
)
