package io.qalipsis.api.events

/**
 * Interface for instances in charge of publishing the [io.qalipsis.core.factories.events.Event]s to external systems.
 *
 * When enabled, the publishers are injected into an [EventsLogger] in order to receive the data.
 * The [EventsPublisher]s should never be used directly from an instance generating an event.
 *
 * @author Eric Jessé
 */
interface EventsPublisher {

    /**
     * Receives an [Event] so that it can be published. The implementation decides what to exactly do:
     * add to a buffer, push to an external system...
     */
    fun publish(event: Event)

    /**
     * Initializes and start the logger.
     */
    fun start() = Unit

    /**
     * Perform sall the closing operations.
     */
    fun stop() = Unit
}
