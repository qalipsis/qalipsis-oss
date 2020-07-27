package io.evolue.plugins.netty

/**
 *
 * @author Eric Jessé
 */
object Pipeline {

    const val PROXY_HANDLER = "proxy.handler"
    const val TLS_HANDLER = "tls.handler"
    const val REQUEST_DECODER = "request.decoder"
    const val REQUEST_ENCODER = "request.encoder"
    const val REQUEST_HANDLER = "request.handler"

    const val MONITORING_CONNECTION_HANDLER = "monitoring.connection.handler"
    const val MONITORING_TLS_HANDLER = "monitoring.tls.handler"
    const val MONITORING_CHANNEL_HANDLER = "monitoring.channel.handler"

}