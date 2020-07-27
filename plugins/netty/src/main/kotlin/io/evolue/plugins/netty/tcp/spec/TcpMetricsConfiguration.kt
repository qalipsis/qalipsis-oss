package io.evolue.plugins.netty.tcp.spec

import io.evolue.plugins.netty.configuration.MetricsConfiguration

data class TcpMetricsConfiguration internal constructor(
    var connectTime: Boolean = false,
    var tlsHandshakeTime: Boolean = false,
    var timeToLastByte: Boolean = false,
    var dataSent: Boolean = false,
    var dataReceived: Boolean = false
) {
    fun all() {
        connectTime = true
        tlsHandshakeTime = true
        timeToLastByte = true
        dataSent = true
        dataReceived = true
    }

    internal fun toMetricsConfiguration() = MetricsConfiguration(
        timeToLastByte = timeToLastByte,
        dataSent = dataSent,
        dataReceived = dataReceived
    )

}
