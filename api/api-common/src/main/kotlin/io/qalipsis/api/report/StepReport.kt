/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.api.report

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.StepName
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Report of a single step within a [ScenarioReport].
 *
 * @author Eric Jessé
 */
@Introspected
@Schema(
    name = "Details about execution report of a step",
    title = "Step-level execution details within a scenario report"
)
data class StepReport(
    @field:Schema(description = "Name of the step")
    val name: StepName,

    @field:Schema(description = "Identifier of the DAG owning this step")
    val dagId: DirectedAcyclicGraphName,

    @field:Schema(description = "Whether the step is subject to load injection")
    val isUnderLoad: Boolean,

    @field:Schema(description = "Whether the step was successfully initialized")
    val initialized: Boolean,

    @field:Schema(description = "Error message when initialization failed", required = false)
    val initializationError: String? = null,

    @field:Schema(description = "Total count of successful executions for this step")
    val successfulExecutions: Long = 0L,

    @field:Schema(description = "Total count of failed executions for this step")
    val failedExecutions: Long = 0L
)
