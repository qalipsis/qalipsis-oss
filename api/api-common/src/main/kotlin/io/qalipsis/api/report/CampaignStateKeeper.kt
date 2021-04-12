package io.qalipsis.api.report

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.context.StepId

/**
 * Service in charge of keep track of the campaign behaviors.
 *
 * @author Eric Jessé
 */
interface CampaignStateKeeper {

    /**
     * Notifies the start of a new campaign.
     */
    fun start(campaignId: CampaignId, scenarioId: ScenarioId)

    /**
     * Notifies the completion of a campaign, whether successful or not.
     */
    fun complete(campaignId: CampaignId, scenarioId: ScenarioId)

    /**
     * Adds a message to notify in the campaign report.
     *
     * @return returns a unique message ID, that can be later used for delete.
     */
    fun put(
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        stepId: StepId,
        severity: ReportMessageSeverity,
        message: String
    ): Any

    /**
     * Deletes a message previously put in the campaign report, for example when a previously failing step is now
     * over the success threshold.
     */
    fun delete(campaignId: CampaignId, scenarioId: ScenarioId, stepId: StepId, messageId: Any)

    /**
     * Increment the counter of the started minions by [count].
     */
    fun recordStartedMinion(campaignId: CampaignId, scenarioId: ScenarioId, count: Int = 1)

    /**
     * Increment the counter of the completed (whether successful or not) minions by [count].
     */
    fun recordCompletedMinion(campaignId: CampaignId, scenarioId: ScenarioId, count: Int = 1)

    /**
     * Increment the counter of the successful step executions by [count].
     */
    fun recordSuccessfulStepExecution(campaignId: CampaignId, scenarioId: ScenarioId, stepId: StepId, count: Int = 1)

    /**
     * Increment the counter of the failed step executions by [count].
     */
    fun recordFailedStepExecution(campaignId: CampaignId, scenarioId: ScenarioId, stepId: StepId, count: Int = 1)

    /**
     * Reports the state of all the scenarios executed in a campaign.
     */
    fun report(campaignId: CampaignId): CampaignReport
}
