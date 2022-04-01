package io.qalipsis.api.report

import io.qalipsis.api.context.StepName

/**
 * Message for a [ScenarioReport].
 *
 * @author Eric Jessé
 */
data class ReportMessage(
    val stepName: StepName,
    val messageId: Any,
    val severity: ReportMessageSeverity,
    val message: String
)
