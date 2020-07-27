package io.evolue.plugins.netty.configuration

import java.net.InetAddress
import java.time.Duration

/**
 * @property connectTimeout time out to establish a connection (default is 10 seconds)
 * @property sendBufferSize size (in bytes) of the sending buffer, default is 1024
 * @property receiveBufferSize size (in bytes) of the receiving buffer, default is 1024
 */
open class ConnectionConfiguration internal constructor(
    var connectTimeout: Duration = Duration.ofSeconds(10),
    var sendBufferSize: Int = 1024,
    var receiveBufferSize: Int = 1024
) {
    internal var host: String? = null
    internal var port: Int? = null

    fun address(host: String, port: Int) {
        this.host = host
        this.port = port
    }

    fun address(address: InetAddress, port: Int) {
        this.host = address.hostAddress
        this.port = port
    }
}