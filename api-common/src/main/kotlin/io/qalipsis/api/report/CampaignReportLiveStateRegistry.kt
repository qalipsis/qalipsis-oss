package io.qalipsis.api.report

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.context.StepId

/**
 * Service in charge of keep track of the campaign behaviors.
 *
 * @author Eric Jessé
 */
interface CampaignReportLiveStateRegistry {

    /**
     * Adds a message to notify in the campaign report.
     *
     * @return returns a unique message ID, that can be later used for delete.
     */
    suspend fun put(
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        stepId: StepId,
        severity: ReportMessageSeverity,
        message: String
    ) = put(campaignId, scenarioId, stepId, severity, null, message)

    /**
     * Adds a message to notify in the campaign report.
     *
     * @return returns a unique message ID, that can be later used for delete.
     */
    suspend fun put(
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        stepId: StepId,
        severity: ReportMessageSeverity,
        messageId: Any? = null,
        message: String
    ): Any

    /**
     * Deletes a message previously put in the campaign report, for example when a previously failing step is now
     * over the success threshold.
     */
    suspend fun delete(campaignId: CampaignId, scenarioId: ScenarioId, stepId: StepId, messageId: Any)

    /**
     * Increments the counter of the started minions by [count].
     *
     * @return the count of currently running minions in the scenario
     */
    suspend fun recordStartedMinion(campaignId: CampaignId, scenarioId: ScenarioId, count: Int = 1): Long

    /**
     * Increments the counter of the completed (whether successful or not) minions by [count].
     *
     * @return the count of currently running minions in the scenario
     */
    suspend fun recordCompletedMinion(campaignId: CampaignId, scenarioId: ScenarioId, count: Int = 1): Long

    /**
     * Increments the counter of the successful step executions by [count].
     *
     * @return the total count of successful executions for the specified step
     */
    suspend fun recordSuccessfulStepExecution(
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        stepId: StepId,
        count: Int = 1
    ): Long

    /**
     * Increments the counter of the failed step executions by [count].
     *
     * @return the total count of failed executions for the specified step
     */
    suspend fun recordFailedStepExecution(
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        stepId: StepId,
        count: Int = 1
    ): Long

}
