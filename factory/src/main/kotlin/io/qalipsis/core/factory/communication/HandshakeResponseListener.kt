package io.qalipsis.core.factory.communication

import io.micronaut.core.order.Ordered
import io.qalipsis.core.handshake.HandshakeResponse

/**
 * A [HandshakeResponseListener] receives [HandshakeResponse]s for processing.
 *
 * @author Eric Jessé
 */
interface HandshakeResponseListener : Ordered {

    /**
     * Processes the [HandshakeResponse].
     */
    suspend fun notify(response: HandshakeResponse)
}
