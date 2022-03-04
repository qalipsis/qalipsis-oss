package io.qalipsis.core.factory.steps

import io.qalipsis.core.factory.orchestration.TransportableContext

/**
 * Service to consume [TransportableContext]s received from remote factories.
 *
 * @author Eric Jessé
 */
internal interface ContextConsumer {

    suspend fun start()

    suspend fun stop()

}
