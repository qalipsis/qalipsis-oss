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

package io.qalipsis.api.context

/**
 * Context to provide to steps to notify that the tail of the convey of executions of a minion reached them and left.
 *
 * @property campaignKey Identifier of the test campaign owning the context
 * @property minionId Identifier of the Minion owning the context
 * @property scenarioName Identifier of the Scenario being executed
 * @property minionStart Instant since epoch in ms, when the minions was started
 * @property lastExecutedStepName Identifier of the lately executed step
 * @property errors List of the errors, from current and previous steps
 *
 * @author Eric Jessé
 *
 */
interface CompletionContext : MonitoringTags {
    val campaignKey: CampaignKey
    val scenarioName: ScenarioName
    val minionId: MinionId
    val minionStart: Long
    val lastExecutedStepName: StepName
    val errors: List<StepError>
}
