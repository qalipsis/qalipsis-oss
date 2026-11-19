/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
 *
 */

package io.qalipsis.core.head.communication

import io.qalipsis.api.Executors.ORCHESTRATION_EXECUTOR_NAME
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.directives.DispatcherChannel
import io.qalipsis.core.feedbacks.CampaignManagementFeedback
import io.qalipsis.core.feedbacks.CampaignMetersFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.heartbeat.Heartbeat
import io.qalipsis.core.serialization.ControlPlaneSerializer
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.slf4j.MDCContext

/**
 * Abstract class to subscribe to channel.
 *
 * @author Joël Valère
 */
abstract class ChannelSubscriber(
    private val serializer: ControlPlaneSerializer,
    private val headConfiguration: HeadConfiguration,
    private val heartbeatListeners: Collection<HeartbeatListener>,
    private val feedbackListeners: Collection<FeedbackListener<*>>,
    private val handshakeRequestListeners: Collection<HandshakeRequestListener>,
    protected val meterFeedbackListeners: Collection<CampaignMeterFeedbackListener>,
    @Named(ORCHESTRATION_EXECUTOR_NAME) private val orchestrationCoroutineScope: CoroutineScope
) {

    val subscribedHandshakeRequestsChannels: MutableSet<DispatcherChannel> = concurrentSet()

    val subscribedFeedbackChannels: MutableSet<DispatcherChannel> = concurrentSet()

    val subscribedMeterFeedbackChannels: MutableSet<DispatcherChannel> = concurrentSet()

    /**
     * Dispatches the [Feedback], [HandshakeRequest] or the [Heartbeat] in isolated coroutines.
     */
    fun deserializeAndDispatch(channel: String, message: ByteArray) {
        try {
            when (channel) {
                in subscribedFeedbackChannels -> dispatch(serializer.deserializeFeedback(message))
                in subscribedMeterFeedbackChannels -> dispatch(serializer.deserializeMetersFeedback(message))
                in subscribedHandshakeRequestsChannels -> dispatch(serializer.deserializeHandshakeRequest(message))
                headConfiguration.heartbeatChannel -> dispatch(serializer.deserializeHeartbeat(message))
                else -> log.trace { "Channel $channel is not supported" }
            }
        } catch (e: Exception) {
            log.error(e) { "An error occurred while processing a message on channel $channel" }
        }
    }

    /**
     * Dispatches the [Feedback] to the relevant [FeedbackListener] in isolated coroutines.
     */
    @Suppress("UNCHECKED_CAST")
    private fun dispatch(feedback: Feedback) {
        try {
            log.trace { "Dispatching the feedback of type ${feedback::class}" }
            val mdcContextMap = (feedback as? CampaignManagementFeedback)?.let {
                mapOf("tenant" to it.tenant, "campaign" to it.campaignKey)
            } ?: emptyMap()
            val eligibleListeners = feedbackListeners.filter { it.accept(feedback) }
            if (eligibleListeners.isNotEmpty()) {
                eligibleListeners.forEach { listener ->
                    log.trace { "Dispatching the directive of type ${feedback::class} to the listener of type ${listener::class}" }
                    orchestrationCoroutineScope.launch(MDCContext(mdcContextMap)) {
                        (listener as FeedbackListener<Feedback>).notify(feedback)
                    }
                }
            }
        } catch (e: Exception) {
            log.error(e) { "An error occurred while processing a feedback: $feedback" }
        }
    }

    /**
     * Dispatches the [CampaignMetersFeedback] to the relevant [CampaignMeterFeedbackListener] in isolated coroutines.
     */
    private fun dispatch(feedback: CampaignMetersFeedback) {
        try {
            log.trace { "Dispatching the meter feedback of type ${feedback::class}" }
            meterFeedbackListeners.forEach { listener ->
                orchestrationCoroutineScope.launch { listener.notify(feedback) }
            }
        } catch (e: Exception) {
            log.error(e) { "An error occurred while processing a meter feedback: $feedback" }
        }
    }

    /**
     * Dispatches the [HandshakeRequest] to all the relevant [HandshakeRequestListener] in isolated coroutines.
     */
    private fun dispatch(request: HandshakeRequest) {
        try {
            log.trace { "Dispatching the handshake request ${request::class}" }
            handshakeRequestListeners.forEach { listener ->
                orchestrationCoroutineScope.launch { listener.notify(request) }
            }
        } catch (e: Exception) {
            log.error(e) { "An error occurred while processing a handshake request: $request" }
        }
    }

    /**
     * Dispatches the [heartbeat] to all the relevant [HeartbeatListener] in isolated coroutines.
     */
    private fun dispatch(heartbeat: Heartbeat) {
        try {
            log.trace { "Dispatching the heartbeat $heartbeat" }
            heartbeatListeners.forEach { listener ->
                orchestrationCoroutineScope.launch { listener.notify(heartbeat) }
            }
        } catch (e: Exception) {
            log.error(e) { "An error occurred while processing a heartbeat: $heartbeat" }
        }
    }

    private companion object {
        val log = logger()
    }
}