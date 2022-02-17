package io.qalipsis.api.context

/**
 * Context to provide to steps to notify that the tail of the convey of executions of a minion reached them and left.
 *
 * @property campaignId Identifier of the test campaign owning the context
 * @property minionId Identifier of the Minion owning the context
 * @property scenarioId Identifier of the Scenario being executed
 * @property lastExecutedStepId Identifier of the lately executed step
 * @property errors List of the errors, from current and previous steps
 *
 * @author Eric Jessé
 *
 */
interface CompletionContext : MonitoringTags {
    val campaignId: CampaignId
    val scenarioId: ScenarioId
    val minionId: MinionId
    val lastExecutedStepId: StepId
    val errors: List<StepError>
}
