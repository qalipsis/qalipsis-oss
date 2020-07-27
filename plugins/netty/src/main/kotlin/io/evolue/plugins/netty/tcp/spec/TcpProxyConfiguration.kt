package io.evolue.plugins.netty.tcp.spec

import java.net.InetAddress

/**
 * @author Eric Jessé
 */
data class TcpProxyConfiguration internal constructor(
    internal var type: TcpProxyType = TcpProxyType.SOCKS4,
    internal var host: String? = null,
    internal var port: Int? = null,
    var username: String? = null,
    var password: String? = null
) {

    fun address(host: String, port: Int) {
        this.host = host
        this.port = port
    }

    fun address(address: InetAddress, port: Int) {
        this.host = address.hostAddress
        this.port = port
    }

}
