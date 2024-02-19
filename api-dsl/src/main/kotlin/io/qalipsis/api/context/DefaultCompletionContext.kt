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
 * Default implementation of [CompletionContext].
 *
 * @author Eric Jessé
 */
data class DefaultCompletionContext(
    override val campaignKey: CampaignKey,
    override val scenarioName: ScenarioName,
    override val minionId: MinionId,
    override val minionStart: Long,
    override val lastExecutedStepName: StepName,
    override val errors: List<StepError>
) : CompletionContext {

    private var eventTags = mapOf(
        "campaign" to campaignKey,
        "minion" to minionId,
        "scenario" to scenarioName,
        "last-executed-step" to lastExecutedStepName,
    )

    private var metersTag = mapOf(
        "campaign" to campaignKey,
        "scenario" to scenarioName
    )

    override fun toEventTags() = eventTags

    override fun toMetersTags() = metersTag
}