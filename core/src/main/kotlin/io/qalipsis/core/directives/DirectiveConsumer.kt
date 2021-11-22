package io.qalipsis.core.directives

/**
 * Parent interface for all the service in charge of consuming the [io.qalipsis.api.orchestration.directives.Directive]s
 * coming from the head or other factories.
 *
 * @author Eric Jessé
 */
interface DirectiveConsumer {

    /**
     * Starts the consumption of the [io.qalipsis.api.orchestration.directives.Directive]s and provides them to the
     * [io.qalipsis.api.orchestration.directives.DirectiveProcessor]s.
     */
    suspend fun start(unicastChannel: String, broadcastChannel: String)

}