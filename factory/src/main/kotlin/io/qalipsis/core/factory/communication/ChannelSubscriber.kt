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

package io.qalipsis.core.factory.communication

import io.qalipsis.api.Executors.ORCHESTRATION_EXECUTOR_NAME
import io.qalipsis.api.coroutines.contextualLaunch
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.directives.CampaignManagementDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveRegistry
import io.qalipsis.core.directives.DispatcherChannel
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.serialization.DistributionSerializer
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.MDC

/**
 * Abstract class to subscribe to channel.
 *
 * @author Joël Valère
 */
abstract class ChannelSubscriber(
    private val serializer: DistributionSerializer,
    private val directiveRegistry: DirectiveRegistry,
    private val factoryConfiguration: FactoryConfiguration,
    private val directiveListeners: List<DirectiveListener<*>>,
    private val handshakeResponseListeners: List<HandshakeResponseListener>,
    @Named(ORCHESTRATION_EXECUTOR_NAME) private val orchestrationCoroutineScope: CoroutineScope
) {

    val subscribedHandshakeResponseChannels: MutableSet<DispatcherChannel> = concurrentSet()

    val subscribedDirectiveChannels: MutableSet<DispatcherChannel> = concurrentSet()

    /**
     * Dispatches the [Directive] or the [HandshakeResponse] in isolated coroutines.
     */
    fun deserializeAndDispatch(channel: String, message: ByteArray) {
        when (channel) {
            in subscribedDirectiveChannels -> serializer.deserialize<Directive>(message)?.let { dispatch(it) }
            in subscribedHandshakeResponseChannels -> serializer.deserialize<HandshakeResponse>(message)
                ?.let { dispatch(it) }

            else -> log.trace { "Channel $channel is not supported" }
        }
    }

    /**
     * Dispatches the [Directive] to the relevant [DirectiveListener] in isolated coroutines.
     */
    @Suppress("UNCHECKED_CAST")
    private fun dispatch(directive: Directive) {
        orchestrationCoroutineScope.launch {
            (directive as? CampaignManagementDirective)?.let {
                MDC.put("tenant", it.tenant)
                MDC.put("campaign", it.campaignKey)
            }
            try {
                log.trace { "Dispatching the directive of type ${directive::class}" }
                val eligibleListeners = directiveListeners.filter { it.accept(directive) }
                if (eligibleListeners.isNotEmpty()) {
                    directiveRegistry.prepareAfterReceived(directive)?.let { preparedDirective ->
                        eligibleListeners.forEach { listener ->
                            log.trace { "Providing the directive of type ${directive::class} to ${listener::class}" }
                            orchestrationCoroutineScope.contextualLaunch {
                                (listener as DirectiveListener<Directive>).notify(preparedDirective)
                            }
                        }
                    }
                }
            } finally {
                MDC.clear()
            }
        }
    }

    /**
     * Dispatches the [HandshakeResponse] to all the relevant [HandshakeResponseListener] in isolated coroutines.
     */
    private fun dispatch(response: HandshakeResponse) {
        if (response.handshakeNodeId == factoryConfiguration.nodeId) {
            log.trace { "Dispatching the handshake response" }
            handshakeResponseListeners.stream().forEach { listener ->
                orchestrationCoroutineScope.launch { listener.notify(response) }
            }
        }
    }

    private companion object {
        val log = logger()
    }
}