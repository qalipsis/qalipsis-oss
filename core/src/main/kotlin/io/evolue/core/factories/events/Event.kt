package io.evolue.core.factories.events

import io.evolue.api.events.EventLevel

/**
 * [EventLevel]s report anything that happened at a particular instant.
 *
 * @author Eric Jessé
 */
class Event(

        /**
         * Name of the event. They are always kebab-cased (dash-separated lowercase words) and in the form of
         * "object-state", e.g: step-started, minion-completed.
         */
        val name: String,

        /**
         * Level of the event, by analogy to the logging libraries. Events with a level lower
         * than the one configured for the reporter are ignored
         */
        val level: EventLevel,

        /**
         * Tags to customize the event.
         */
        val tags: Collection<EventTag> = emptyList(),

        /**
         * Value associated with the event.
         */
        val value: Any?
) {
    /**
     * Instant when the event occurred.
     */
    val timestamp: Long = System.currentTimeMillis()
}

class EventTag(val key: String, val value: String)

fun Map<String, String>.toTags(): Collection<EventTag> {
    return entries.map { e -> EventTag(e.key, e.value) }
}
