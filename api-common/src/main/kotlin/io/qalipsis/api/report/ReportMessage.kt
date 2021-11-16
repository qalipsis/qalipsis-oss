package io.qalipsis.api.report

import io.qalipsis.api.context.StepId

/**
 * Message for a [ScenarioReport].
 *
 * @author Eric Jessé
 */
data class ReportMessage(
    val stepId: StepId,
    val messageId: Any,
    val severity: ReportMessageSeverity,
    val message: String
)
