package io.qalipsis.core.factory.events

import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.events.Event
import io.qalipsis.api.events.EventLevel
import io.qalipsis.api.events.EventTag
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.events.EventsPublisher
import io.qalipsis.api.events.toTags
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.configuration.ExecutionEnvironments
import jakarta.inject.Singleton
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Default implementation of the [EventsLogger], that forwards the events to the [EventsPublisher] when the event level
 * has higher than the one configured for its path.
 *
 * @author Eric Jessé
 */
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
@Singleton
internal class EventsLoggerImpl(
    configuration: EventsLoggerConfiguration,
    private val publishers: Collection<EventsPublisher>
) : EventsLogger {

    /**
     * Tags to systematically add to all the generated events.
     */
    private val tags = mutableListOf<EventTag>()

    /**
     * Default level for all the non-otherwise-configured [Event]s.
     */
    private val rootLevel = configuration.root

    /**
     * Consolidated configuration of the the level for the [Event]s.
     */
    private val declaredLevels = configuration.level
        .toList()
        // The longest events first.
        .sortedByDescending { it.first }

    /**
     * Consolidated levels of the so-far-generated and configured [Event]s.
     */
    private val actualLevels = ConcurrentHashMap<Int, EventLevel>()

    /**
     * Checks whether there is a reason to enable the logger or not.
     */
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

    override fun configureTags(tags: Map<String, String>) {
        this.tags.clear()
        this.tags += tags.map { (key, value) -> EventTag(key, value) }
    }

    override fun log(
        level: EventLevel, name: String, value: Any?, timestamp: Instant,
        tagsSupplier: () -> Map<String, String>
    ) {
        logMethodWithSupplier?.invoke(level, name, value, timestamp, tagsSupplier)
    }

    override fun log(level: EventLevel, name: String, value: Any?, timestamp: Instant, tags: Map<String, String>) {
        logMethod?.invoke(level, name, value, timestamp, tags)
    }

    @KTestable
    private fun checkLevelAndLogWithSupplier(
        level: EventLevel, name: String, value: Any?, timestamp: Instant,
        tagsSupplier: () -> Map<String, String>
    ) {
        val actualLevel = findActualEventLevel(name)
        if (level >= actualLevel) {
            publishers.asSequence()
                .forEach { it.publish(Event(name, level, this.tags + tagsSupplier().toTags(), value, timestamp)) }
        }
    }

    @KTestable
    private fun checkLevelAndLog(
        level: EventLevel, name: String, value: Any?, timestamp: Instant,
        tags: Map<String, String>
    ) {
        val actualLevel = findActualEventLevel(name)
        if (level >= actualLevel) {
            publishers.asSequence()
                .forEach { it.publish(Event(name, level, this.tags + tags.toTags(), value, timestamp)) }
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
