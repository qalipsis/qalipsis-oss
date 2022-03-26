package io.qalipsis.core.factory.communication

import io.qalipsis.core.handshake.HandshakeResponse

/**
 * A [HandshakeResponseListener] receives [HandshakeResponse]s for processing.
 *
 * @author Eric Jessé
 */
interface HandshakeResponseListener {

    /**
     * Processes the [HandshakeResponse].
     */
    suspend fun notify(response: HandshakeResponse)
}
