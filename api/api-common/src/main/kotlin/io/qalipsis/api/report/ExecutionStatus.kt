package io.qalipsis.api.report

/**
 * Execution status of a [ScenarioReport] or [CampaignReport].
 *
 * @author Eric Jessé
 */
enum class ExecutionStatus(val exitCode: Int) {
    SUCCESSFUL(0),
    WARNING(0),
    FAILED(1),
    ABORTED(2)
}
