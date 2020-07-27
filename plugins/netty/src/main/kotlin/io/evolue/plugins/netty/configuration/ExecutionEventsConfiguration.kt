package io.evolue.plugins.netty.configuration

/**
 *
 * @author Eric Jessé
 */
data class ExecutionEventsConfiguration(
    var sending: Boolean = false,
    var sent: Boolean = false,
    var receiving: Boolean = false,
    var received: Boolean = false
) {
    fun all() {
        sending = true
        sent = true
        receiving = true
        received = true
    }
}