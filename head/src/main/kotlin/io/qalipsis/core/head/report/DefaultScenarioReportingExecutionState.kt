package io.qalipsis.core.head.report

import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import java.time.Instant

/**
 * Implementation of [ScenarioReportingExecutionState] to created from an external source.
 *
 * @author Eric Jessé
 */
internal data class DefaultScenarioReportingExecutionState(
    override val scenarioId: ScenarioId,
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
