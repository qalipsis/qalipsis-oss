package io.qalipsis.core.factory.init

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.DispatcherChannel
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.communication.HandshakeResponseListener
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.heartbeat.Heartbeat
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
import javax.annotation.PreDestroy

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
    @Named(Executors.BACKGROUND_EXECUTOR_NAME) private val coroutineScope: CoroutineScope
) : HandshakeResponseListener {

    private lateinit var nodeId: String

    private lateinit var heartbeatChannel: DispatcherChannel

    private var running = true

    private var heartbeatJob: Job? = null

    override suspend fun notify(response: HandshakeResponse) {
        heartbeatChannel = response.heartbeatChannel
        nodeId = response.nodeId
        heartbeatJob = coroutineScope.launch {
            try {
                while (running) {
                    val heartbeat = Heartbeat(
                        nodeId,
                        Instant.now(),
                        Heartbeat.State.HEALTHY,
                        campaignManager.runningCampaign.campaignKey.takeUnless(String::isBlank)
                    )
                    try {
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
                it.cancelAndJoin()

                factoryChannel.publishHeartbeat(
                    heartbeatChannel, Heartbeat(
                        nodeId,
                        Instant.now(),
                        Heartbeat.State.UNREGISTERED
                    )
                )
            }
        }
    }

    private companion object {

        val log = logger()
    }
}
