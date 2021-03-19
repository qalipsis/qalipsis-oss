package io.qalipsis.api.events

import java.time.Instant
import javax.validation.constraints.DecimalMax
import javax.validation.constraints.DecimalMin

/**
 * Level for the severity / priority of the events.
 *
 * @author Eric Jessé
 */
enum class EventLevel() {
    // The order of the declarations matters to perform comparisons.
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    OFF;
}

/**
 * [Event]s report anything that happened at a particular instant.
 *
 * @property name name of the event. They are always dot separated groups of kebab-cased strings (dash-separated lowercase words) and in the form of "object-state", e.g: runner.step-started, minions-keeper.minion.execution-complete.
 * @property level level of the event, by analogy to the logging libraries. Events with a level lower than the one configured for the reporter are ignored.
 * @property tags tags to customize the event identity, defaults to an empty list.
 * @property value optional value attache to the event, defaults to null.
 * @property timestamp instant when the event occurred, defaults to the current instant.
 *
 * @see [EventsLogger]
 * @see [EventLevel]
 * @see [EventTag]
 * @see [EventGeoPoint]
 * @see [EventRange]
 *
 * @author Eric Jessé
 */
data class Event(
    val name: String,
    val level: EventLevel,
    val tags: Collection<EventTag> = emptyList(),
    val value: Any? = null,
    val timestamp: Instant = Instant.now()
)

/**
 * Unique tag to customize the identity of an event.
 *
 * @author Eric Jessé
 */
data class EventTag(val key: String, val value: String)

fun Map<String, String>.toTags(): List<EventTag> {
    return entries.map { e -> EventTag(e.key, e.value) }
}


/**
 * Use a [EventGeoPoint] to set a geographical position as a value for an [Event].
 *
 * @property latitude the latitude of the point in degrees
 * @property longitude the longitude of the point in degrees
 * @property altitude the optional altitude of the point in meters, defaults to null
 *
 * @author Eric Jessé
 */
data class EventGeoPoint(
    @field:DecimalMin("-90")
    @field:DecimalMax("90")
    val latitude: Double,

    @field:DecimalMin("-180")
    @field:DecimalMax("180")
    val longitude: Double,

    val altitude: Double? = null
)

/**
 * Use a [EventRange] to set a range of number as a value for an [Event].
 *
 * @property lowerBound the lower bound of the range
 * @property upperBound the upper bound of the range
 * @property includeLower specify whether the lower bound is included in the range, defaults to true
 * @property includeUpper specify whether the upper bound is included in the range, defaults to true
 *
 */
data class EventRange<T : Number>(val lowerBound: T, val upperBound: T, val includeLower: Boolean = true,
                                  val includeUpper: Boolean = true)
