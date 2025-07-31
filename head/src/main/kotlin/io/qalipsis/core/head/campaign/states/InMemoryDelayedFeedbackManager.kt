/*
 * QALIPSIS
 * Copyright (C) 2024 AERIS IT Solutions GmbH
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

package io.qalipsis.core.head.campaign.states

import io.micronaut.context.annotation.Secondary
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.TaskScheduler
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.directives.DispatcherChannel
import io.qalipsis.core.feedbacks.CampaignManagementFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.head.communication.HeadChannel
import io.qalipsis.core.head.configuration.HeadConfiguration
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking

/**
 * Implementation of [DelayedFeedbackManager] that performs the operation in memory.
 */
@Singleton
@Secondary
class InMemoryDelayedFeedbackManager(
    @Named(TaskExecutors.SCHEDULED) private val taskScheduler: TaskScheduler,
    private val headChannel: HeadChannel,
    private val configuration: HeadConfiguration,
) : DelayedFeedbackManager {

    @LogInput
    override fun scheduleCancellation(channelName: DispatcherChannel, feedback: Feedback) {
        taskScheduler.schedule(configuration.campaignCancellationStateGracePeriod) {
            runBlocking {
                headChannel.publishFeedback(
                    channelName = channelName,
                    campaignKey = (feedback as CampaignManagementFeedback).campaignKey,
                    serializedFeedback = feedback
                )
            }
        }
    }

}