package io.evolue.plugins.netty.tcp.spec

import io.evolue.plugins.netty.configuration.ConnectionConfiguration
import io.evolue.plugins.netty.configuration.TlsConfiguration

/**
 * @property noDelay if set to `true` (default), the Nagle's algorithm is disabled
 * @property closeOnFailure if set to `true` (default),  the connection is closed when the step fails
 */
class TcpConnectionConfiguration internal constructor(
    var noDelay: Boolean = true,
    var closeOnFailure: Boolean = true,
    internal var tlsConfiguration: TlsConfiguration? = null,
    internal var proxyConfiguration: TcpProxyConfiguration? = null
) : ConnectionConfiguration() {

    fun proxy(configurationBlock: TcpProxyConfiguration.() -> Unit) {
        this.proxyConfiguration = TcpProxyConfiguration().also { it.configurationBlock() }
    }

    fun tls(configurationBlock: TlsConfiguration.() -> Unit) {
        this.tlsConfiguration = TlsConfiguration()
            .also { it.configurationBlock() }
    }
}
