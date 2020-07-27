package io.evolue.plugins.netty.tcp.spec

import io.evolue.plugins.netty.configuration.EventsConfiguration

data class TcpEventsConfiguration internal constructor(
    var connection: Boolean = false,
    var sending: Boolean = false,
    var sent: Boolean = false,
    var receiving: Boolean = false,
    var received: Boolean = false
) {

    fun all() {
        connection = true
        sending = true
        sent = true
        receiving = true
        received = true
    }

    internal fun toEventsConfiguration() = EventsConfiguration(
        sending = sending,
        sent = sent,
        receiving = receiving,
        received = received
    )

}
