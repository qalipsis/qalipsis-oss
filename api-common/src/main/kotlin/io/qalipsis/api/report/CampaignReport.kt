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
import java.time.Instant

/**
 * Aggregated report of all the scenarios of a campaign.
 *
 * @author Eric Jessé
 */
data class CampaignReport(
    val campaignKey: CampaignKey,
    val start: Instant,
    val end: Instant?,
    val startedMinions: Int = 0,
    val completedMinions: Int = 0,
    val successfulExecutions: Int = 0,
    val failedExecutions: Int = 0,
    val status: ExecutionStatus,
    val scenariosReports: List<ScenarioReport> = emptyList()
)
