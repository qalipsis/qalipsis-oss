package io.evolue.plugins.netty.configuration

data class EventsConfiguration internal constructor(
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

    internal fun toExecutionEventsConfiguration() =
        ExecutionEventsConfiguration(
            sending = sending,
            sent = sent,
            receiving = receiving,
            received = received
        )
}
