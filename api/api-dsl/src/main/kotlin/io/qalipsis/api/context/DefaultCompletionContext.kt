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