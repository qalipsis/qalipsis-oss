package io.evolue.api.events

/**
 *
 *
 * @author Eric Jessé
 */
interface EventsLogger {

    /**
     * Log an event with the provided level.
     *
     * @param level the priority of the event.
     * @param name the name of the event. They are always kebab-cased (dash-separated lowercase words) and in the form of
     * "object-state", e.g: step-started, minion-completed.
     * @param value the potential value. Any type can be used, its interpretation is let to the implementation.
     * @param tagsSupplier block to generate the map of tags, if the event ever has to be logged.
     */
    fun log(level: EventLevel, name: String, value: Any? = null,
        tagsSupplier: (() -> Map<String, String>))

    /**
     * Log an event with the provided level. If creating the map of tags has a cost, prefer to the use
     * the equivalent method with a tags supplier expression.
     *
     * @param level the priority of the event.
     * @param name the name of the event. They are always kebab-cased (dash-separated lowercase words) and in the form of
     * "object-state", e.g: step-started, minion-completed.
     * @param value the potential value. Any type can be used, its interpretation is let to the implementation.
     * @param tags a map of tags.
     */
    fun log(level: EventLevel, name: String, value: Any? = null,
        tags: Map<String, String> = emptyMap())

    fun log(level: EventLevel, name: String, value: Any? = null, vararg tags: Pair<String, String>) {
        log(level, name, value) { tags.toMap() }
    }

    fun trace(name: String, value: Any? = null, tags: Map<String, String> = emptyMap()) {
        log(EventLevel.TRACE, name, value, tags)
    }

    fun trace(name: String, value: Any? = null, vararg tags: Pair<String, String>) {
        log(EventLevel.TRACE, name, value, *tags)
    }

    fun trace(name: String, value: Any? = null, tagsSupplier: (() -> Map<String, String>)) {
        log(EventLevel.TRACE, name, value, tagsSupplier)
    }

    fun debug(name: String, value: Any? = null, tags: Map<String, String> = emptyMap()) {
        log(EventLevel.DEBUG, name, value, tags)
    }

    fun debug(name: String, value: Any? = null, vararg tags: Pair<String, String>) {
        log(EventLevel.DEBUG, name, value, *tags)
    }

    fun debug(name: String, value: Any? = null, tagsSupplier: (() -> Map<String, String>)) {
        log(EventLevel.DEBUG, name, value, tagsSupplier)
    }

    fun info(name: String, value: Any? = null, tags: Map<String, String> = emptyMap()) {
        log(EventLevel.INFO, name, value, tags)
    }

    fun info(name: String, value: Any? = null, vararg tags: Pair<String, String>) {
        log(EventLevel.INFO, name, value, *tags)
    }

    fun info(name: String, value: Any? = null, tagsSupplier: (() -> Map<String, String>)) {
        log(EventLevel.INFO, name, value, tagsSupplier)
    }

    fun warn(name: String, value: Any? = null, tags: Map<String, String> = emptyMap()) {
        log(EventLevel.WARN, name, value, tags)
    }

    fun warn(name: String, value: Any? = null, vararg tags: Pair<String, String>) {
        log(EventLevel.WARN, name, value, *tags)
    }

    fun warn(name: String, value: Any? = null, tagsSupplier: (() -> Map<String, String>)) {
        log(EventLevel.WARN, name, value, tagsSupplier)
    }

    fun error(name: String, value: Any? = null, tags: Map<String, String> = emptyMap()) {
        log(EventLevel.ERROR, name, value, tags)
    }

    fun error(name: String, value: Any? = null, vararg tags: Pair<String, String>) {
        log(EventLevel.ERROR, name, value, *tags)
    }

    fun error(name: String, value: Any? = null, tagsSupplier: (() -> Map<String, String>)) {
        log(EventLevel.ERROR, name, value, tagsSupplier)
    }

    /**
     * Initialize and start the logger.
     */
    fun start() {
        // By default, nothing to do. The implementation is optional.
    }

    /**
     * Perform all the closing operations.
     */
    fun stop() {
        // By default, nothing to do. The implementation is optional.
    }
}
