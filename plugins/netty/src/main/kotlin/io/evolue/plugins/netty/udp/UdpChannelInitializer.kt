package io.evolue.plugins.netty.udp

import io.evolue.plugins.netty.ClientExecutionContext
import io.evolue.plugins.netty.Pipeline
import io.evolue.plugins.netty.configuration.EventsConfiguration
import io.evolue.plugins.netty.configuration.MetricsConfiguration
import io.evolue.plugins.netty.handlers.AbstractChannelInitializer
import io.evolue.plugins.netty.handlers.ByteArrayRequestHandler
import io.evolue.plugins.netty.monitoring.EventsRecorder
import io.evolue.plugins.netty.monitoring.MetricsRecorder
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.handler.codec.DatagramPacketDecoder
import io.netty.handler.codec.bytes.ByteArrayDecoder
import io.netty.handler.codec.bytes.ByteArrayEncoder
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicReference

/**
 * Channel initializer for UDP clients, using a [ByteArrayRequestHandler] to process the data.
 *
 * @author Eric Jessé
 */
internal class UdpChannelInitializer(
    private val metricsConfiguration: MetricsConfiguration,
    private val eventsConfiguration: EventsConfiguration,
    executionContext: AtomicReference<ClientExecutionContext>,
    private val inboundChannel: Channel<ByteArray>,
    private val resultChannel: Channel<Result<ByteArray>>,
    private val metricsRecorder: MetricsRecorder,
    private val eventsRecorder: EventsRecorder
) : AbstractChannelInitializer<NioDatagramChannel>(metricsConfiguration,
    eventsConfiguration, executionContext, metricsRecorder, eventsRecorder) {

    override fun configureRequestHandlers(channel: NioDatagramChannel) {
        channel.pipeline().addLast(Pipeline.REQUEST_DECODER, DatagramPacketDecoder(ByteArrayDecoder()))
        channel.pipeline().addLast(Pipeline.REQUEST_ENCODER, ByteArrayEncoder())
        channel.pipeline().addLast(Pipeline.REQUEST_HANDLER, ByteArrayRequestHandler(inboundChannel, resultChannel))
    }

    override fun requiresChannelMonitoring(): Boolean {
        return metricsConfiguration.dataReceived
                || metricsConfiguration.dataSent
                || metricsConfiguration.timeToLastByte
                || eventsConfiguration.receiving
                || eventsConfiguration.received
                || eventsConfiguration.sending
                || eventsConfiguration.sent
    }
}
