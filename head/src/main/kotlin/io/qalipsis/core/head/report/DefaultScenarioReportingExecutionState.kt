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

package io.qalipsis.core.head.report

import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import java.time.Instant

/**
 * Implementation of [ScenarioReportingExecutionState] to created from an external source.
 *
 * @author Eric Jessé
 */
internal data class DefaultScenarioReportingExecutionState(
    override val scenarioName: ScenarioName,
    override val start: Instant,
    override val startedMinions: Int,
    override val completedMinions: Int,
    override val successfulStepExecutions: Int,
    override val failedStepExecutions: Int,
    override var end: Instant?,
    override var abort: Instant?,
    override val status: ExecutionStatus?,
    override val messages: Map<Any, ReportMessage>
) : ScenarioReportingExecutionState
