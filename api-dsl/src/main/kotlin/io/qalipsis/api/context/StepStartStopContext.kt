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