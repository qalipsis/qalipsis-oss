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

package io.qalipsis.core.head.inmemory

import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.core.head.report.ScenarioReportingExecutionState
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * Implementation of [ScenarioReportingExecutionState] to constantly keep the state into memory.
 *
 * @author Eric Jessé
 */
data class InMemoryScenarioReportingExecutionState(
    override val scenarioName: ScenarioName
) : ScenarioReportingExecutionState {
    override val start: Instant = Instant.now()

    val startedMinionsCounter = AtomicInteger()

    val completedMinionsCounter = AtomicInteger()

    val successfulStepExecutionsCounter = AtomicInteger()

    val failedStepExecutionsCounter = AtomicInteger()

    val keyedMessages = linkedMapOf<String, ReportMessage>()

    override var end: Instant? = null

    override var abort: Instant? = null

    override var status: ExecutionStatus? = null

    override val messages: List<ReportMessage>
        get() = keyedMessages.values.toList()

    override val startedMinions: Int
        get() = startedMinionsCounter.get()

    override val completedMinions: Int
        get() = completedMinionsCounter.get()

    override val successfulStepExecutions: Int
        get() = successfulStepExecutionsCounter.get()

    override val failedStepExecutions: Int
        get() = failedStepExecutionsCounter.get()
}