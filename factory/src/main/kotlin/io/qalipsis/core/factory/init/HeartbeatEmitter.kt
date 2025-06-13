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

package io.qalipsis.core.factory.init

import io.micronaut.context.annotation.Requires
import io.micronaut.core.order.Ordered
import io.qalipsis.api.Executors
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.DispatcherChannel
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.communication.HandshakeResponseListener
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.heartbeat.Heartbeat
import jakarta.annotation.PreDestroy
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Instant

/**
 * Generates an heartbeat on a regular basis.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.STANDALONE, ExecutionEnvironments.FACTORY])
internal class HeartbeatEmitter(
    private val factoryChannel: FactoryChannel,
    private val campaignManager: FactoryCampaignManager,
    private val factoryConfiguration: FactoryConfiguration,
    @Named(Executors.BACKGROUND_EXECUTOR_NAME) private val coroutineScope: CoroutineScope
) : HandshakeResponseListener {

    private lateinit var nodeId: String

    private lateinit var heartbeatChannel: DispatcherChannel

    private var running = true

    private var heartbeatJob: Job? = null

    @LogInput
    override suspend fun notify(response: HandshakeResponse) {
        heartbeatChannel = response.heartbeatChannel
        nodeId = response.nodeId
        log.trace { "Starting the heartbeat routine" }
        heartbeatJob = coroutineScope.launch {
            try {
                while (running) {
                    val heartbeat = Heartbeat(
                        nodeId,
                        factoryConfiguration.tenant,
                        Instant.now(),
                        Heartbeat.State.IDLE,
                        campaignManager.runningCampaign.campaignKey.takeUnless(String::isBlank)
                    )
                    try {
                        log.trace { "Sending $heartbeat to $heartbeatChannel" }
                        factoryChannel.publishHeartbeat(heartbeatChannel, heartbeat)
                    } catch (e: Exception) {
                        if (e is CancellationException) {
                            throw e
                        } else {
                            log.error(e) { "An error occurred while publishing the heartbeat $heartbeat" }
                        }
                    }

                    delay(response.heartbeatPeriod.toMillis())
                }
            } catch (e: CancellationException) {
                // Ignore the exception.
            }
        }
    }

    @PreDestroy
    fun close() {
        // If the heartbeat job was started, it is stopped and an unregistration state is sent.
        heartbeatJob?.let {
            runBlocking(coroutineScope.coroutineContext) {
                kotlin.runCatching {
                    it.cancelAndJoin()
                }

                factoryChannel.publishHeartbeat(
                    heartbeatChannel, Heartbeat(
                        nodeId,
                        factoryConfiguration.tenant,
                        Instant.now(),
                        Heartbeat.State.OFFLINE
                    )
                )
            }
        }
    }

    override fun getOrder(): Int {
        return Ordered.LOWEST_PRECEDENCE
    }

    private companion object {

        val log = logger()
    }
}
