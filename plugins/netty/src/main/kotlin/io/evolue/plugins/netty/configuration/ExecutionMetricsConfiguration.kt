package io.evolue.plugins.netty.configuration

/**
 *
 * @author Eric Jessé
 */
data class ExecutionMetricsConfiguration(
    var timeToLastByte: Boolean = false,
    var dataSent: Boolean = false,
    var dataReceived: Boolean = false
) {
    fun all() {
        timeToLastByte = true
        dataSent = true
        dataReceived = true
    }
}