package io.qalipsis.core.factory.configuration

import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.context.event.BeanDestroyedEvent
import io.micronaut.context.event.BeanDestroyedEventListener
import io.qalipsis.api.events.EventsLogger
import jakarta.inject.Singleton

/**
 * Implementation of [BeanCreatedEventListener] in order to start and stop the [EventsLogger]s.
 */
@Singleton
internal class EventLoggerCreationListener(
    private val factoryConfiguration: FactoryConfiguration
) : BeanCreatedEventListener<EventsLogger>,
    BeanDestroyedEventListener<EventsLogger> {

    override fun onCreated(event: BeanCreatedEvent<EventsLogger>): EventsLogger {
        event.bean.apply {
            configureTags(factoryConfiguration.selectors)
            start()
        }
        return event.bean
    }

    override fun onDestroyed(event: BeanDestroyedEvent<EventsLogger>) {
        event.bean.stop()
    }

}