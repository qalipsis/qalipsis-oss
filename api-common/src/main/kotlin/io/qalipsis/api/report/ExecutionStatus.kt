package io.qalipsis.api.report

/**
 * Execution status of a [ScenarioReport] or [CampaignReport].
 *
 * @property SUCCESSFUL all the steps, were successful
 * @property WARNING a deeper look at the reports is required, but the campaign does not fail
 * @property FAILED the campaign went until the end, but got errors
 * @property ABORTED the campaign was aborted, either by a user or a critical failure
 *
 * @author Eric Jessé
 */
enum class ExecutionStatus(val exitCode: Int) {
    SUCCESSFUL(0),
    WARNING(0),
    FAILED(1),
    ABORTED(2)
}
