package io.qalipsis.core.head.report

import io.qalipsis.core.head.jdbc.entity.FactoryStateValue

/**
 * Custom data class to return factory states for UI use.
 *
 * @author Francisca Eze
 */
internal data class FactoryState(
    val idle: Int, val registered: Int, val unhealthy: Int, val offline: Int,
) {
    companion object {
        fun convertToWrapperClass(map: Map<FactoryStateValue, Int>): FactoryState {
            return object {
                val registered = map.getOrDefault(FactoryStateValue.REGISTERED, 0)
                val idle = map.getOrDefault(FactoryStateValue.IDLE, 0)
                val unhealthy = map.getOrDefault(FactoryStateValue.UNHEALTHY, 0)
                val offline = map.getOrDefault(FactoryStateValue.OFFLINE, 0)

                val data = FactoryState(idle, registered, unhealthy, offline)
            }.data
        }
    }
}