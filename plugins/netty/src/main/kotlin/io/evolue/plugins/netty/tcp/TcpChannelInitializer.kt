package io.evolue.plugins.netty.tcp

import io.evolue.api.context.StepContext
import io.evolue.plugins.netty.ClientExecutionContext
import io.evolue.plugins.netty.Pipeline
import io.evolue.plugins.netty.configuration.TlsConfiguration
import io.evolue.plugins.netty.handlers.AbstractChannelInitializer
import io.evolue.plugins.netty.handlers.ByteArrayRequestHandler
import io.evolue.plugins.netty.handlers.monitoring.ConnectionMonitoringHandler
import io.evolue.plugins.netty.handlers.monitoring.TlsMonitoringHandler
import io.evolue.plugins.netty.monitoring.EventsRecorder
import io.evolue.plugins.netty.monitoring.MetricsRecorder
import io.evolue.plugins.netty.tcp.spec.TcpEventsConfiguration
import io.evolue.plugins.netty.tcp.spec.TcpMetricsConfiguration
import io.evolue.plugins.netty.tcp.spec.TcpProxyConfiguration
import io.evolue.plugins.netty.tcp.spec.TcpProxyType
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.bytes.ByteArrayDecoder
import io.netty.handler.codec.bytes.ByteArrayEncoder
import io.netty.handler.proxy.Socks4ProxyHandler
import io.netty.handler.proxy.Socks5ProxyHandler
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslHandler
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import kotlinx.coroutines.channels.Channel
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

/**
 * Channel initializer for TCP clients, using a [ByteArrayRequestHandler] to process the data.
 *
 * @author Eric Jessé
 */
internal class TcpChannelInitializer(
    private val tlsConfiguration: TlsConfiguration?,
    private val proxyConfiguration: TcpProxyConfiguration?,
    private val metricsConfiguration: TcpMetricsConfiguration,
    private val eventsConfiguration: TcpEventsConfiguration,
    private val channelMonitoringForOtherStepsEnabled: Boolean,
    executionContext: AtomicReference<ClientExecutionContext>,
    private val inboundChannel: Channel<ByteArray>,
    private val resultChannel: Channel<Result<ByteArray>>,
    private val metricsRecorder: MetricsRecorder,
    private val eventsRecorder: EventsRecorder
) : AbstractChannelInitializer<SocketChannel>(metricsConfiguration.toMetricsConfiguration(),
    eventsConfiguration.toEventsConfiguration(), executionContext, metricsRecorder, eventsRecorder) {

    private fun configureProxyHandler(channel: SocketChannel) {
        if (proxyConfiguration != null) {
            val proxyHandler = when (proxyConfiguration.type) {
                TcpProxyType.SOCKS4 -> Socks4ProxyHandler(
                    InetSocketAddress(proxyConfiguration.host!!, proxyConfiguration.port!!),
                    proxyConfiguration.username)
                TcpProxyType.SOCKS5 -> Socks5ProxyHandler(
                    InetSocketAddress(proxyConfiguration.host!!, proxyConfiguration.port!!),
                    proxyConfiguration.username, proxyConfiguration.password)
            }
            channel.pipeline().addFirst(Pipeline.PROXY_HANDLER, proxyHandler)
        }
    }

    override fun configureMonitoringHandlers(channel: SocketChannel, stepContext: StepContext<*, *>) {
        if (metricsConfiguration.connectTime || eventsConfiguration.connection) {
            val connectionMetricsRecorder = if (metricsConfiguration.connectTime) metricsRecorder else null
            val connectionEventsRecorder = if (eventsConfiguration.connection) eventsRecorder else null
            channel.pipeline().addFirst(Pipeline.MONITORING_CONNECTION_HANDLER,
                ConnectionMonitoringHandler(connectionMetricsRecorder, connectionEventsRecorder, stepContext))
        }

        if (metricsConfiguration.tlsHandshakeTime && tlsConfiguration != null) {
            channel.pipeline().addAfter(Pipeline.TLS_HANDLER, Pipeline.MONITORING_TLS_HANDLER,
                TlsMonitoringHandler(metricsRecorder, stepContext))
        }
        super.configureMonitoringHandlers(channel, stepContext)
    }

    override fun configureRequestHandlers(channel: SocketChannel) {
        configureProxyHandler(channel)
        configureTlsHandler(channel)
        channel.pipeline().addLast(Pipeline.REQUEST_DECODER, ByteArrayDecoder())
        channel.pipeline().addLast(Pipeline.REQUEST_ENCODER, ByteArrayEncoder())
        channel.pipeline().addLast(Pipeline.REQUEST_HANDLER, ByteArrayRequestHandler(inboundChannel, resultChannel))
    }

    private fun configureTlsHandler(channel: SocketChannel) {
        if (tlsConfiguration != null) {
            val sslContextBuilder = if (tlsConfiguration.disableCertificateVerification) {
                SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
            } else {
                SslContextBuilder.forClient()
            }
            val sslEngine = sslContextBuilder.build().newEngine(channel.alloc())
            if (tlsConfiguration.protocols.isNotEmpty()) {
                sslEngine.enabledProtocols = tlsConfiguration.protocols
            } else {
                sslEngine.enabledProtocols = sslEngine.supportedProtocols
            }
            val pipeline = channel.pipeline()
            if (pipeline.get(Pipeline.PROXY_HANDLER) != null) {
                channel.pipeline().addAfter(Pipeline.PROXY_HANDLER, Pipeline.TLS_HANDLER, SslHandler(sslEngine))
            } else {
                channel.pipeline().addFirst(Pipeline.TLS_HANDLER, SslHandler(sslEngine))
            }
        }
    }

    override fun requiresChannelMonitoring(): Boolean {
        return channelMonitoringForOtherStepsEnabled || metricsConfiguration.dataReceived
                || metricsConfiguration.dataSent
                || metricsConfiguration.timeToLastByte
                || eventsConfiguration.receiving
                || eventsConfiguration.received
                || eventsConfiguration.sending
                || eventsConfiguration.sent
    }
}
