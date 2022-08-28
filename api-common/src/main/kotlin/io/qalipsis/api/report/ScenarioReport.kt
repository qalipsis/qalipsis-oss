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
import java.time.Instant

/**
 * Report of a test campaign for a given scenario.
 *
 * @author Eric Jessé
 */
data class ScenarioReport(
    val campaignKey: CampaignKey,
    val scenarioName: ScenarioName,
    val start: Instant,
    val end: Instant,
    val startedMinions: Int,
    val completedMinions: Int,
    val successfulExecutions: Int,
    val failedExecutions: Int,
    val status: ExecutionStatus,
    val messages: List<ReportMessage>
)
