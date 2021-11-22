package io.qalipsis.core.handshake

import kotlinx.coroutines.Job

/**
 * Communication channel for the handshake, from the factory perspective.
 *
 * @author Eric Jessé
 */
interface HandshakeFactoryChannel {

    suspend fun send(request: HandshakeRequest)

    /**
     * Configures an action to trigger when a HandshakeResponse is received.
     */
    suspend fun onReceiveResponse(subscriberId: String, block: suspend (HandshakeResponse) -> Unit): Job

    /**
     * Closes all the activity of the [HandshakeFactoryChannel] once the handshake was performed.
     */
    suspend fun close()
}