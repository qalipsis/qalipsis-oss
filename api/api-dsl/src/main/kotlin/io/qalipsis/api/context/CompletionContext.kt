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
