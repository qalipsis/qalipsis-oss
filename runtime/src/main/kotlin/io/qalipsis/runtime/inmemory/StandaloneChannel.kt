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

package io.qalipsis.runtime.inmemory

import io.micronaut.context.BeanProvider
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DispatcherChannel
import io.qalipsis.core.directives.SingleUseDirective
import io.qalipsis.core.factory.communication.DirectiveListener
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.feedbacks.CampaignManagementFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.head.communication.FeedbackListener
import io.qalipsis.core.head.communication.HeadChannel
import io.qalipsis.core.heartbeat.Heartbeat
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.event.Level

@Singleton
@Requires(env = [ExecutionEnvironments.STANDALONE])
internal class StandaloneChannel(
    private val listeners: BeanProvider<Listeners>,
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) private val orchestrationCoroutineScope: CoroutineScope,
) : HeadChannel, FactoryChannel {

    override fun subscribeHandshakeResponse(vararg channelNames: DispatcherChannel) = Unit

    override fun unsubscribeHandshakeResponse(vararg channelNames: DispatcherChannel) = Unit

    override fun subscribeDirective(vararg channelNames: DispatcherChannel) = Unit

    override fun unsubscribeDirective(vararg channelNames: DispatcherChannel) = Unit

    override fun subscribeHandshakeRequest(vararg channelNames: DispatcherChannel) = Unit

    override fun unsubscribeHandshakeRequest(vararg channelNames: DispatcherChannel) = Unit

    override fun subscribeFeedback(vararg channelNames: DispatcherChannel) = Unit

    override fun unsubscribeFeedback(vararg channelNames: DispatcherChannel) = Unit

    @LogInput(level = Level.DEBUG)
    override suspend fun publishDirective(directive: Directive) {
        val verifiedDirective = if (directive is SingleUseDirective<*>) {
            directive.toReference("")
        } else {
            directive
        }
        listeners.get().directiveListeners.stream().filter { it.accept(verifiedDirective) }.forEach { listener ->
            @Suppress("UNCHECKED_CAST")
            orchestrationCoroutineScope.launch { (listener as DirectiveListener<Directive>).notify(directive) }
        }
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun publishHandshakeResponse(
        channelName: DispatcherChannel,
        handshakeResponse: HandshakeResponse
    ) {
        listeners.get().handshakeResponseListeners.stream().forEach { listener ->
            orchestrationCoroutineScope.launch { listener.notify(handshakeResponse) }
        }
    }

    override fun getStartupOrder() = Int.MIN_VALUE

    override fun init() {
        super<HeadChannel>.init()
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun publishDirective(channel: DispatcherChannel, directive: Directive) {
        publishDirective(directive)
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun publishFeedback(feedback: Feedback) {
        if (feedback is CampaignManagementFeedback) {
            feedback.nodeId = STANDALONE_FACTORY_NAME
        }
        listeners.get().feedbackListeners.stream().filter { it.accept(feedback) }.forEach { listener ->
            @Suppress("UNCHECKED_CAST")
            orchestrationCoroutineScope.launch { (listener as FeedbackListener<Feedback>).notify(feedback) }
        }
    }

    override suspend fun publishFeedback(
        channelName: DispatcherChannel,
        campaignKey: String,
        serializedFeedback: Any
    ) {
        publishFeedback(serializedFeedback as Feedback)
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun publishHandshakeRequest(handshakeRequest: HandshakeRequest) {
        listeners.get().handshakeRequestListeners.stream().forEach { listener ->
            orchestrationCoroutineScope.launch { listener.notify(handshakeRequest) }
        }
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun publishHeartbeat(channel: DispatcherChannel, heartbeat: Heartbeat) {
        listeners.get().heartbeatListeners.stream().forEach { listener ->
            orchestrationCoroutineScope.launch { listener.notify(heartbeat) }
        }
    }

    private companion object {
        const val STANDALONE_FACTORY_NAME = "_embedded_"

        val log = logger()
    }
}