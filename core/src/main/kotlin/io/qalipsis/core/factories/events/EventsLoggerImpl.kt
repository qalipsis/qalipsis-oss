package io.qalipsis.core.factories.events

import io.qalipsis.api.annotations.VisibleForTest
import io.qalipsis.api.events.Event
import io.qalipsis.api.events.EventLevel
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.events.EventsPublisher
import io.qalipsis.api.events.toTags
import io.qalipsis.api.logging.LoggerHelper.logger
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton

/**
 * Default implementation of the [EventsLogger], that forwards the events to the [EventsPublisher] when the event level
 * has higher than the one configured for its path.
 *
 * @author Eric Jessé
 */
@Singleton
internal class EventsLoggerImpl(
    configuration: EventsLoggerConfiguration,
    private val publishers: Collection<EventsPublisher>
) : EventsLogger {

    private val rootLevel = configuration.root

    private val actualLevels = ConcurrentHashMap<Int, EventLevel>()

    private val declaredLevels = configuration.level
        .map { (key, level) ->
            key as String to EventLevel.valueOf("$level".toUpperCase())
        }
        // The longest events first.
        .sortedByDescending { it.first }

    private val enabled =
        publishers.isNotEmpty() && (rootLevel < EventLevel.OFF || declaredLevels.any { it.second < EventLevel.OFF })

    /**
     * Method to use to log an event with a tags supplier when the logging is enabled.
     */
    private val logMethodWithSupplier =
        if (enabled) this::checkLevelAndLogWithSupplier else null

    /**
     * Method to use to log an event with a tags map when the logging is enabled.
     */
    private val logMethod = if (enabled) this::checkLevelAndLog else null

    override fun start() {
        if (enabled) {
            publishers.forEach(EventsPublisher::start)
        }
    }

    override fun stop() {
        if (enabled) {
            publishers.forEach(EventsPublisher::stop)
        }
    }

    override fun log(level: EventLevel, name: String, value: Any?, timestamp: Instant,
                     tagsSupplier: () -> Map<String, String>) {
        logMethodWithSupplier?.invoke(level, name, value, timestamp, tagsSupplier)
    }

    override fun log(level: EventLevel, name: String, value: Any?, timestamp: Instant, tags: Map<String, String>) {
        logMethod?.invoke(level, name, value, timestamp, tags)
    }

    @VisibleForTest
    fun checkLevelAndLogWithSupplier(level: EventLevel, name: String, value: Any?, timestamp: Instant,
                                     tagsSupplier: () -> Map<String, String>) {
        val actualLevel = findActualEventLevel(name)
        if (level >= actualLevel) {
            publishers.asSequence()
                .forEach { it.publish(Event(name, level, tagsSupplier().toTags(), value, timestamp)) }
        }
    }

    @VisibleForTest
    fun checkLevelAndLog(level: EventLevel, name: String, value: Any?, timestamp: Instant,
                         tags: Map<String, String>) {
        val actualLevel = findActualEventLevel(name)
        if (level >= actualLevel) {
            publishers.asSequence().forEach { it.publish(Event(name, level, tags.toTags(), value, timestamp)) }
        }
    }

    /**
     * Finds out the actual level to apply to the events with the given name.
     * The longest configured level matching the start of [name] is used.
     * Once the value was first evaluated, it is then kept into the cache.
     */
    private fun findActualEventLevel(name: String) = actualLevels.computeIfAbsent(name.hashCode()) {
        declaredLevels.firstOrNull { (configKey, _) -> name.startsWith(configKey) }?.second ?: rootLevel
    }

    companion object {
        @JvmStatic
        val log = logger()
    }
}
