package io.qalipsis.core.head.report

import io.micronaut.core.annotation.Introspected

/**
 * Custom data class to return factory states for UI use.
 *
 * @author Francisca Eze
 */
@Introspected
internal data class FactoryState(
    val idle: Int, val registered: Int, val unhealthy: Int, val offline: Int,
)