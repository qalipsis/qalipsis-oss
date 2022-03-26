package io.qalipsis.core.factory.events

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.core.naming.conventions.StringConvention
import io.qalipsis.api.events.EventLevel
import io.qalipsis.core.configuration.ExecutionEnvironments
import javax.annotation.PostConstruct


/**
 * Configuration of the events logging.
 *
 * @property root level of the events, when not explicitly defined in [level]
 * @property level explicit of level to filter the events
 *
 * @author Eric Jessé
 */
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
@ConfigurationProperties("events")
class EventsLoggerConfiguration(
    val environment: Environment
) {
    var root: EventLevel = EventLevel.TRACE

    lateinit var level: Map<String, EventLevel>

    @PostConstruct
    fun init() {
        val properties: MutableMap<String, Any> = HashMap(environment.getProperties("events.level"))
        // Using raw keys here allows configuring log levels for camelCase package names in application.yml
        properties.putAll(environment.getProperties("events.level", StringConvention.RAW))
        val levels = mutableMapOf<String, EventLevel>()
        properties.forEach { (loggerPrefix, levelValue) ->
            levels[loggerPrefix] = EventLevel.valueOf("$levelValue".uppercase())
        }
        level = levels
    }

}
