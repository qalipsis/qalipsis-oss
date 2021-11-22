package io.qalipsis.core.handshake

import kotlinx.coroutines.Job

/**
 * Communication channel for the handshake, from the head perspective.
 *
 * @author Eric Jessé
 */
interface HandshakeHeadChannel {

    /**
     * Configures an action to trigger when a HandshakeRequest is received.
     */
    suspend fun onReceiveRequest(subscriberId: String, block: suspend (HandshakeRequest) -> Unit): Job

    /**
     * Sends a handshake response to the factory.
     */
    suspend fun sendResponse(channelName: String, response: HandshakeResponse)

}