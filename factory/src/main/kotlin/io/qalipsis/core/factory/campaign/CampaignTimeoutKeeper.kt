/*
 * QALIPSIS
 * Copyright (C) 2023 AERIS IT Solutions GmbH
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
package io.qalipsis.core.factory.campaign

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.Executors
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.feedbacks.CampaignTimeoutFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus.COMPLETED
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * Service in charge of activating / deactivating the timeouts of the campaign to run in the factory.
 */
@Singleton
class CampaignTimeoutKeeper(
    private val factoryChannel: FactoryChannel,
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) private val coroutineScope: CoroutineScope
) : CampaignLifeCycleAware {

    @KTestable
    private var hardTimerJob: Job? = null

    @KTestable
    private var softTimerJob: Job? = null

    override suspend fun init(campaign: Campaign) {
        super.init(campaign)
        softTimerJob = campaign.softTimeout?.let { timeout ->
            startTimeout(timeout, false, campaign.campaignKey, SOFT_TIMEOUT_MESSAGE)
        }
        hardTimerJob = campaign.hardTimeout?.let { timeout ->
            startTimeout(timeout, true, campaign.campaignKey, HARD_TIMEOUT_MESSAGE)
        }
    }


    /**
     * Creates a simple timer that publishes a feedback after timeout.
     *
     * @param timeout number of seconds the thread is delayed before a feedback is sent
     * @param isHardTimeout boolean field that states if the running campaign should terminate softly or hardly
     * @param campaignKey campaign to which the feedback relates
     * @param message message to add into the timeout feedback
     */
    private suspend fun startTimeout(
        timeout: Instant,
        isHardTimeout: Boolean,
        campaignKey: CampaignKey,
        message: String
    ): Job {
        return coroutineScope.launch {
            delay(Duration.between(Instant.now(), timeout).toMillis())
            factoryChannel.publishFeedback(
                CampaignTimeoutFeedback(
                    campaignKey = campaignKey,
                    hard = isHardTimeout,
                    status = COMPLETED,
                    errorMessage = message
                )
            )
        }
    }

    override suspend fun close(campaign: Campaign) {
        hardTimerJob?.cancel()
        softTimerJob?.cancel()
        hardTimerJob = null
        softTimerJob = null
        super.close(campaign)
    }

    private companion object {
        const val HARD_TIMEOUT_MESSAGE = "The hard timeout was reached"
        const val SOFT_TIMEOUT_MESSAGE = "The soft timeout was reached"
    }
}