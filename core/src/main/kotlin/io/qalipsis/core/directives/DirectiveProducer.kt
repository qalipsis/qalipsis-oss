package io.qalipsis.core.directives

/**
 * Service to produce the directive from heads to the factories. The service can also be used by the factories
 * in the case when the head delegates the conduction of an operation to a factory (generally computation intensive
 * or scenario-related operations)
 *
 * @author Eric Jessé
 */
interface DirectiveProducer {

    /**
     * Publish a directive to all the factories.
     */
    suspend fun publish(directive: Directive)

}
