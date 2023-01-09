/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.core.head.inmemory

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.query.Page
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ExecutionStatus.IN_PROGRESS
import io.qalipsis.api.report.ExecutionStatus.QUEUED
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.Scenario
import io.qalipsis.core.head.model.converter.CampaignConfigurationConverter
import jakarta.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(env = [ExecutionEnvironments.TRANSIENT])
)
internal class InMemoryCampaignService(
    private val campaignConfigurationConverter: CampaignConfigurationConverter
) : CampaignService {

    private var currentCampaign: Campaign? = null

    private var currentCampaignConfiguration: CampaignConfiguration? = null

    private val updateLock = Mutex(false)

    override suspend fun create(
        tenant: String,
        configurer: String,
        campaignConfiguration: CampaignConfiguration
    ): RunningCampaign {
        val runningCampaign = campaignConfigurationConverter.convertConfiguration(tenant, campaignConfiguration)

        updateLock.withLock {
            currentCampaignConfiguration = campaignConfiguration
            currentCampaign = Campaign(
                version = Instant.now(),
                key = runningCampaign.key,
                creation = Instant.now(),
                name = campaignConfiguration.name,
                speedFactor = campaignConfiguration.speedFactor,
                scheduledMinions = campaignConfiguration.scenarios.values.sumOf { it.minionsCount },
                hardTimeout = campaignConfiguration.hardTimeout,
                start = null,
                end = null,
                status = QUEUED,
                configurerName = null,
                aborterName = null,
                scenarios = campaignConfiguration.scenarios.map {
                    Scenario(
                        Instant.now(),
                        it.key,
                        it.value.minionsCount
                    )
                }
            )
            currentCampaign!!
        }

        return runningCampaign
    }

    override suspend fun retrieve(tenant: String, campaignKey: CampaignKey): Campaign {
        return currentCampaign!!
    }

    override suspend fun retrieveConfiguration(tenant: String, campaignKey: CampaignKey): CampaignConfiguration {
        return currentCampaignConfiguration!!
    }

    override suspend fun prepare(tenant: String, campaignKey: CampaignKey) {
        updateLock.withLock {
            currentCampaign = currentCampaign?.copy(status = IN_PROGRESS)
        }
    }

    override suspend fun start(tenant: String, campaignKey: CampaignKey, start: Instant, timeout: Instant?) {
        updateLock.withLock {
            currentCampaign = currentCampaign?.copy(start = Instant.now(), timeout = timeout)
        }
    }

    override suspend fun startScenario(
        tenant: String,
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        start: Instant
    ) = Unit

    override suspend fun closeScenario(tenant: String, campaignKey: CampaignKey, scenarioName: ScenarioName) = Unit

    override suspend fun close(
        tenant: String,
        campaignKey: CampaignKey,
        result: ExecutionStatus,
        failureReason: String?
    ): Campaign {
        return updateLock.withLock {
            val currentFailureReason = currentCampaign?.failureReason
            currentCampaign = currentCampaign!!.copy(
                end = Instant.now(),
                status = result,
                failureReason = currentFailureReason ?: failureReason
            )
            currentCampaign!!
        }
    }

    override suspend fun search(
        tenant: String, filters: Collection<String>, sort: String?, page: Int, size: Int
    ): Page<Campaign> {
        // Nothing to do.
        return Page(0, 0, 0, emptyList())
    }

    override suspend fun abort(tenant: String, aborter: String, campaignKey: String) {
        currentCampaign = currentCampaign!!.copy(end = Instant.now(), status = ExecutionStatus.ABORTED)
    }

    override suspend fun enrich(runningCampaign: RunningCampaign) {
        currentCampaign = currentCampaign!!.copy(failureReason = runningCampaign.message)
    }
}