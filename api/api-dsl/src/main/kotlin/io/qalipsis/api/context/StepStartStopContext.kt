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
 * Data class to pass to a step when starting and stopping it.
 *
 * @property campaignKey identifier of the test campaign owning the context
 * @property scenarioName identifier of the Scenario being executed
 * @property dagId identifier of the DirectedAcyclicGraph being executed
 * @property stepName identifier of the Step being initialized
 * @property properties contains properties to start the steps
 *
 * @author Eric Jessé
 */
data class StepStartStopContext(
    val campaignKey: CampaignKey,
    val scenarioName: ScenarioName,
    val dagId: DirectedAcyclicGraphName,
    val stepName: StepName,
    val properties: Map<String, String> = emptyMap()
) : MonitoringTags {

    override fun toEventTags(): Map<String, String> {
        return mapOf(
            "campaign" to campaignKey,
            "scenario" to scenarioName,
            "dag" to dagId,
            "step" to stepName,
        )
    }

    override fun toMetersTags(): Map<String, String> {
        return mapOf(
            "campaign" to campaignKey,
            "scenario" to scenarioName,
            "dag" to dagId,
            "step" to stepName
        )
    }
}