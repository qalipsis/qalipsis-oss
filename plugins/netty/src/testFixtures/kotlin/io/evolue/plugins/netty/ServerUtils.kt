package io.evolue.plugins.netty

import java.net.DatagramSocket
import java.net.ServerSocket

object ServerUtils {

    /**
     * Find an available TCP port on the local host.
     */
    fun availableTcpPort(): Int {
        return ServerSocket(0).use { it.localPort }
    }

    /**
     * Find an available UDP port on the local host.
     */
    fun availableUdpPort(): Int {
        return DatagramSocket(0).use { it.localPort }
    }
}
