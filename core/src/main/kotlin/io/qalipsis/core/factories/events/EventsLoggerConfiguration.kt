package io.qalipsis.core.factories.events

import io.micronaut.context.annotation.ConfigurationProperties
import io.qalipsis.api.events.EventLevel
import java.util.Properties

/**
 * Configuration of the events logging.
 *
 * @property root level of the events, when not explicitly defined in [level]
 * @property level explicit of level to filter the events
 *
 * @author Eric Jessé
 */
@ConfigurationProperties("events")
class EventsLoggerConfiguration {
    var root: EventLevel = EventLevel.TRACE

    var level: Properties = Properties()
}
