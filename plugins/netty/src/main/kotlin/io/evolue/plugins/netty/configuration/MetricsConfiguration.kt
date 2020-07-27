package io.evolue.plugins.netty.configuration

data class MetricsConfiguration internal constructor(
    var timeToLastByte: Boolean = false,
    var dataSent: Boolean = false,
    var dataReceived: Boolean = false
) {
    fun all() {
        timeToLastByte = true
        dataSent = true
        dataReceived = true
    }

    internal fun toExecutionMetricsConfiguration() =
        ExecutionMetricsConfiguration(
            timeToLastByte = timeToLastByte,
            dataSent = dataSent,
            dataReceived = dataReceived
        )

}
