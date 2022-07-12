package io.qalipsis.core.head.report

import io.micronaut.core.annotation.Introspected

/**
 * The sharing mode with the other members of the tenant.
 *
 * @author Palina Bril
 */
@Introspected
internal enum class SharingMode {
    READONLY, WRITE, NONE
}

/**
 * Type of data to fetch
 *
 * @author Palina Bril
 */
@Introspected
internal enum class DataType {
    METERS, EVENTS
}