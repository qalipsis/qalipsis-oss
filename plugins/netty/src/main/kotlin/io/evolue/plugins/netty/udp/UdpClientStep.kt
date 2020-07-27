package io.evolue.plugins.netty.udp

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
import io.evolue.api.retry.RetryPolicy
import io.evolue.plugins.netty.AbstractClientStep
import io.evolue.plugins.netty.ClientExecutionContext
import io.evolue.plugins.netty.configuration.ConnectionConfiguration
import io.evolue.plugins.netty.configuration.EventsConfiguration
import io.evolue.plugins.netty.configuration.MetricsConfiguration
import io.evolue.plugins.netty.monitoring.EventsRecorder
import io.evolue.plugins.netty.monitoring.MetricsRecorder
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.socket.nio.NioDatagramChannel
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicReference

/**
 * Step to send and receive data using UDP.
 *
 * @author Eric Jessé
 */
internal class UdpClientStep<I>(
    id: StepId,
    retryPolicy: RetryPolicy?,
    private val requestBlock: suspend (input: I) -> ByteArray,
    private val connectionConfiguration: ConnectionConfiguration,
    private val metricsConfiguration: MetricsConfiguration,
    private val eventsConfiguration: EventsConfiguration,
    private val metricsRecorder: MetricsRecorder,
    private val eventsRecorder: EventsRecorder
) : AbstractClientStep<I, ByteArray>(id, retryPolicy, connectionConfiguration, metricsConfiguration,
    eventsConfiguration) {

    override fun Bootstrap.initBootstrap(inboundChannel: Channel<ByteArray>, resultChannel: Channel<Result<ByteArray>>,
                                         connectionExecutionContext: AtomicReference<ClientExecutionContext>): Bootstrap {
        return channel(NioDatagramChannel::class.java)
            .handler(UdpChannelInitializer(
                metricsConfiguration,
                eventsConfiguration,
                connectionExecutionContext,
                inboundChannel,
                resultChannel,
                metricsRecorder,
                eventsRecorder
            ))
    }

    override suspend fun doExecute(channel: ChannelFuture, context: StepContext<I, Pair<I, ByteArray>>,
                                   inboundChannel: Channel<ByteArray>, resultChannel: Channel<Result<ByteArray>>,
                                   connectionExecutionContext: AtomicReference<ClientExecutionContext>) {
        try {
            val input = context.input.receive()
            val request = requestBlock(input)

            inboundChannel.send(request)
            val result = resultChannel.receive()

            if (result.isSuccess) {
                context.output.send(input to result.getOrThrow())
            } else {
                throw result.exceptionOrNull()!!
            }
        } catch (t: Throwable) {
            throw t
        } finally {
            channel.channel().close()
        }
    }
}
