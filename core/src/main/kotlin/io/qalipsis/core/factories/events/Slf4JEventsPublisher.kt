package io.qalipsis.core.factories.events

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.events.Event
import io.qalipsis.api.events.EventLevel
import io.qalipsis.api.events.EventsPublisher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton

/**
 * Event publisher using Slf4j.
 *
 * The events are saved according to their name prefixed by 'events'.
 *
 * This uses the root logger "events" and is enabled when the property 'events.export.slf4j.enabled' is set to true.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(property = "events.export.slf4j.enabled", value = "true")
internal class Slf4JEventsPublisher : EventsPublisher {

    private val loggers = ConcurrentHashMap<String, Logger>()

    override fun publish(event: Event) {
        val logger = loggers.computeIfAbsent("$LOGGER_PREFIX.${event.name}", LoggerFactory::getLogger)

        when (event.level) {
            EventLevel.ERROR -> logger.error(toMessage(event))
            EventLevel.WARN -> logger.warn(toMessage(event))
            EventLevel.INFO -> logger.info(toMessage(event))
            EventLevel.DEBUG -> logger.debug(toMessage(event))
            EventLevel.TRACE -> logger.trace(toMessage(event))
            else -> Unit
        }
    }

    private fun toMessage(event: Event): String {
        return event.run { "${name};${value ?: ""};${tags.joinToString(",") { "${it.key}=${it.value}" }}" }
    }

    companion object {

        private const val LOGGER_PREFIX = "events."

    }

}
