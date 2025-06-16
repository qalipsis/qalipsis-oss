/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.api.report

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepName

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
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        severity: ReportMessageSeverity,
        message: String
    ) = put(campaignKey, scenarioName, stepName, severity, null, message)

    /**
     * Adds a message to notify in the campaign report.
     *
     * @return returns a unique message ID, that can be later used for delete.
     */
    suspend fun put(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        severity: ReportMessageSeverity,
        messageId: String? = null,
        message: String
    ): String

    /**
     * Deletes a message previously put in the campaign report, for example when a previously failing step is now
     * over the success threshold.
     */
    suspend fun delete(campaignKey: CampaignKey, scenarioName: ScenarioName, stepName: StepName, messageId: Any)

    /**
     * Increments the counter of the started minions by [count].
     *
     * @return the count of currently running minions in the scenario
     */
    suspend fun recordStartedMinion(campaignKey: CampaignKey, scenarioName: ScenarioName, count: Int = 1)

    /**
     * Increments the counter of the completed (whether successful or not) minions by [count].
     *
     * @return the count of currently running minions in the scenario
     */
    suspend fun recordCompletedMinion(campaignKey: CampaignKey, scenarioName: ScenarioName, count: Int = 1)

    /**
     * Increments the counter of the successful step executions by [count].
     *
     * @return the total count of successful executions for the specified step
     */
    suspend fun recordSuccessfulStepExecution(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        count: Int = 1
    )

    /**
     * Increments the counter of the failed step executions by [count].
     *
     * @return the total count of failed executions for the specified step
     */
    suspend fun recordFailedStepExecution(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        count: Int = 1,
        cause: Throwable? = null,
    )


    /**
     * Records the successful initialization of a step.
     */
    suspend fun recordSuccessfulStepInitialization(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName
    )

    /**
     * Records the failed initialization of a step.
     */
    suspend fun recordFailedStepInitialization(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        cause: Throwable? = null,
    )

}
