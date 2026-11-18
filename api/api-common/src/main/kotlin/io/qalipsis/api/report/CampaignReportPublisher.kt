/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.api.report

import io.micronaut.core.order.Ordered
import io.qalipsis.api.context.CampaignKey

/**
 * Service in charge of publishing the campaign reports.
 *
 * @author Palina Bril
 */
interface CampaignReportPublisher : Ordered {

    /**
     * Publishes a report for a completed campaign.
     *
     * @param tenant the tenant owning the campaign
     * @param campaignKey the key identifying the campaign
     * @param report report of the completed campaign
     */
    suspend fun publish(tenant: String, campaignKey: CampaignKey, report: CampaignReport)

}