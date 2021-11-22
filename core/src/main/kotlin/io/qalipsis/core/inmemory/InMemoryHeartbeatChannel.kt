package io.qalipsis.core.inmemory

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.messaging.broadcastTopic
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.heartbeat.Heartbeat
import io.qalipsis.core.heartbeat.HeartbeatConsumer
import io.qalipsis.core.heartbeat.HeartbeatEmitter
import jakarta.inject.Singleton
import kotlinx.coroutines.Job
import java.time.Duration
import java.time.Instant
import javax.annotation.PreDestroy

/**
 * Standalone implementation of [HeartbeatEmitter] and [HeartbeatConsumer] that just runs once
 * to update the status.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.STANDALONE])
internal class InMemoryHeartbeatChannel : HeartbeatEmitter, HeartbeatConsumer {

    private val heartbeats = broadcastTopic<Heartbeat>()

    override suspend fun start(channelName: String, onReceive: suspend (Heartbeat) -> Unit): Job {
        return heartbeats.subscribe("any").onReceiveValue { heartbeat -> onReceive(heartbeat) }
    }

    override suspend fun start(factoryNodeId: String, channelName: String, period: Duration) {
        heartbeats.produceValue(Heartbeat(factoryNodeId, Instant.now()))
    }

    @PreDestroy
    fun closeTopics() {
        kotlin.runCatching {
            heartbeats.close()
        }
    }
}