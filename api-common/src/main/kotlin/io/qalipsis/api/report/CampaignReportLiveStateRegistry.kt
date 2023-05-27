/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
        messageId: Any? = null,
        message: String
    ): Any

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
    suspend fun recordStartedMinion(campaignKey: CampaignKey, scenarioName: ScenarioName, count: Int = 1): Long

    /**
     * Increments the counter of the completed (whether successful or not) minions by [count].
     *
     * @return the count of currently running minions in the scenario
     */
    suspend fun recordCompletedMinion(campaignKey: CampaignKey, scenarioName: ScenarioName, count: Int = 1): Long

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
    ): Long

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
    ): Long

}
