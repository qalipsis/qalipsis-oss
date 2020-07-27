package io.evolue.plugins.netty.handlers.monitoring

import io.evolue.api.context.StepContext
import io.evolue.plugins.netty.monitoring.EventsRecorder
import io.evolue.plugins.netty.monitoring.MetricsRecorder
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.util.concurrent.Future
import java.net.SocketAddress

/**
 * Channel handler to record the connection meters and events.
 *
 * @author Eric Jessé
 */
internal class ConnectionMonitoringHandler(
    private val metricsRecorder: MetricsRecorder?,
    private val eventsRecorder: EventsRecorder?,
    private val connectionStepContext: StepContext<*, *>
) : ChannelOutboundHandlerAdapter() {

    override fun connect(ctx: ChannelHandlerContext, remoteAddress: SocketAddress?, localAddress: SocketAddress?,
        promise: ChannelPromise) {
        eventsRecorder?.recordConnecting(connectionStepContext)
        val connectTimeStart = System.nanoTime()
        super.connect(ctx, remoteAddress, localAddress, promise)
        promise.addListener { future: Future<in Void> ->
            val now = System.nanoTime()
            ctx.pipeline().remove(this)
            if (future.isSuccess) {
                metricsRecorder?.recordSuccessfulConnectionTime(connectionStepContext, now - connectTimeStart)
                eventsRecorder?.recordSuccessfulConnection(connectionStepContext)
            } else {
                metricsRecorder?.recordFailedConnectionTime(connectionStepContext, now - connectTimeStart)
                eventsRecorder?.recordFailedConnection(connectionStepContext)
            }
        }
    }
}
