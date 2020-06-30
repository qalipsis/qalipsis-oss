package io.evolue.api.events

/**
 * Level for the severity / priority of the events.
 *
 * @author Eric Jessé
 */
enum class EventLevel {
    // The order of the declarations matters.
    TRACE, DEBUG, INFO, WARN, ERROR, OFF
}

/**
 * Representation of a geo point for the event value.
 */
data class EventGeoPoint(val latitude: Double, val longitude: Double)

data class EventRange<T : Number>(val lowerBound: T, val upperBound: T)
