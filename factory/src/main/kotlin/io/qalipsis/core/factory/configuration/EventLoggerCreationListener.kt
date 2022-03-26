package io.qalipsis.core.factory.configuration

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.context.event.BeanDestroyedEvent
import io.micronaut.context.event.BeanDestroyedEventListener
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.core.configuration.ExecutionEnvironments
import jakarta.inject.Singleton

/**
 * Implementation of [BeanCreatedEventListener] in order to start and stop the [EventsLogger]s.
 */
@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
internal class EventLoggerCreationListener(
    @Property(name = "factory.tags", defaultValue = "") private val tags: Map<String, String>
) : BeanCreatedEventListener<EventsLogger>,
    BeanDestroyedEventListener<EventsLogger> {

    override fun onCreated(event: BeanCreatedEvent<EventsLogger>): EventsLogger {
        event.bean.apply {
            configureTags(tags)
            start()
        }
        return event.bean
    }

    override fun onDestroyed(event: BeanDestroyedEvent<EventsLogger>) {
        event.bean.stop()
    }

}