package io.qalipsis.core.head.communication

import io.qalipsis.core.handshake.HandshakeRequest

/**
 * A [HandshakeRequestListener] receives [HandshakeRequest]s for processing.
 *
 * @author Eric Jessé
 */
interface HandshakeRequestListener {

    /**
     * Processes the [HandshakeRequest].
     */
    suspend fun notify(handshakeRequest: HandshakeRequest)
}
