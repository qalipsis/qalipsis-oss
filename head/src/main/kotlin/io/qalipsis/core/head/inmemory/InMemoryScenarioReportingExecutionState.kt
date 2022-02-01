package io.qalipsis.core.head.inmemory

import io.qalipsis.api.context.ScenarioId
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
    override val scenarioId: ScenarioId
) : ScenarioReportingExecutionState {
    override val start: Instant = Instant.now()

    val startedMinionsCounter = AtomicInteger()

    val completedMinionsCounter = AtomicInteger()

    val successfulStepExecutionsCounter = AtomicInteger()

    val failedStepExecutionsCounter = AtomicInteger()

    override var end: Instant? = null

    override var abort: Instant? = null

    override var status: ExecutionStatus? = null

    override val messages = linkedMapOf<Any, ReportMessage>()

    override val startedMinions: Int
        get() = startedMinionsCounter.get()

    override val completedMinions: Int
        get() = completedMinionsCounter.get()

    override val successfulStepExecutions: Int
        get() = successfulStepExecutionsCounter.get()

    override val failedStepExecutions: Int
        get() = failedStepExecutionsCounter.get()
}