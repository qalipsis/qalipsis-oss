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
        try {
            when (channel) {
                in subscribedDirectiveChannels -> dispatch(serializer.deserializeDirective(message))
                in subscribedHandshakeResponseChannels -> dispatch(serializer.deserializeHandshakeResponse(message))

                else -> log.trace { "Channel $channel is not supported" }
            }
        } catch (e: Exception) {
            log.error(e) { "An error occurred while processing a message on channel $channel" }
        }
    }

    /**
     * Dispatches the [Directive] to the relevant [DirectiveListener] in isolated coroutines.
     */
    @Suppress("UNCHECKED_CAST")
    private fun dispatch(directive: Directive) {
        try {
            val mdcContextMap = (directive as? CampaignManagementDirective)?.let {
                mapOf("tenant" to it.tenant, "campaign" to it.campaignKey)
            } ?: emptyMap()
            orchestrationCoroutineScope.launch(MDCContext(mdcContextMap)) {
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
            }
        } catch (e: Exception) {
            log.error(e) { "An error occurred while processing a directive: $directive" }
        }
    }

    /**
     * Dispatches the [HandshakeResponse] to all the relevant [HandshakeResponseListener] in isolated coroutines.
     */
    private fun dispatch(response: HandshakeResponse) {
        if (response.handshakeNodeId == factoryConfiguration.nodeId) {
            try {
                log.trace { "Dispatching the handshake response" }
                handshakeResponseListeners.forEach { listener ->
                    orchestrationCoroutineScope.launch { listener.notify(response) }
                }
            } catch (e: Exception) {
                log.error(e) { "An error occurred while processing a handshake response: $response" }
            }
        }
    }

    private companion object {
        val log = logger()
    }
}