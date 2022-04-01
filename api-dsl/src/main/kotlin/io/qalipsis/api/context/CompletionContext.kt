package io.qalipsis.api.context

/**
 * Context to provide to steps to notify that the tail of the convey of executions of a minion reached them and left.
 *
 * @property campaignName Identifier of the test campaign owning the context
 * @property minionId Identifier of the Minion owning the context
 * @property scenarioName Identifier of the Scenario being executed
 * @property lastExecutedStepName Identifier of the lately executed step
 * @property errors List of the errors, from current and previous steps
 *
 * @author Eric Jessé
 *
 */
interface CompletionContext : MonitoringTags {
    val campaignName: CampaignName
    val scenarioName: ScenarioName
    val minionId: MinionId
    val lastExecutedStepName: StepName
    val errors: List<StepError>
}
