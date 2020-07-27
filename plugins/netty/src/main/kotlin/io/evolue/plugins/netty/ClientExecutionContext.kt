package io.evolue.plugins.netty

import io.evolue.api.context.StepContext
import io.evolue.plugins.netty.configuration.ExecutionEventsConfiguration
import io.evolue.plugins.netty.configuration.ExecutionMetricsConfiguration

/**
 *
 * Short-live context of a single execution on a Netty client.
 *
 * @author Eric Jessé
 */
internal data class ClientExecutionContext(
    val stepContext: StepContext<*, *>,
    val metricsConfiguration: ExecutionMetricsConfiguration,
    val eventsConfiguration: ExecutionEventsConfiguration
) {
    private val values = mutableMapOf<String, Any>()

    fun put(key: String, value: Any) {
        values[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T {
        return values[key] as T
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getIfPresent(key: String): T? {
        return values[key] as T?
    }
}
