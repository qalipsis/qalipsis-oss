package io.qalipsis.core.inmemory

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.messaging.broadcastTopic
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.handshake.HandshakeFactoryChannel
import io.qalipsis.core.handshake.HandshakeHeadChannel
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import jakarta.inject.Singleton
import kotlinx.coroutines.Job
import javax.annotation.PreDestroy

/**
 * Implementation of [HandshakeHeadChannel] and [HandshakeFactoryChannel],
 * used for deployments where the head and a unique factory run in the same JVM.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.STANDALONE])
internal class InMemoryHandshakeChannel : HandshakeHeadChannel, HandshakeFactoryChannel {

    private val requests = broadcastTopic<HandshakeRequest>()

    private val responses = broadcastTopic<HandshakeResponse>()

    @LogInputAndOutput
    override suspend fun send(request: HandshakeRequest) {
        requests.produceValue(request)
    }

    @LogInputAndOutput
    override suspend fun onReceiveRequest(subscriberId: String, block: suspend (HandshakeRequest) -> Unit): Job {
        return requests.subscribe(subscriberId).onReceiveValue(block)
    }

    override suspend fun onReceiveResponse(subscriberId: String, block: suspend (HandshakeResponse) -> Unit): Job {
        return responses.subscribe(subscriberId).onReceiveValue(block)
    }

    @LogInputAndOutput
    override suspend fun sendResponse(channelName: String, response: HandshakeResponse) {
        responses.produceValue(response)
    }

    override suspend fun close() {
        closeTopics()
    }

    @PreDestroy
    fun closeTopics() {
        kotlin.runCatching {
            requests.close()
            responses.close()
        }
    }
}