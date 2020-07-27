package io.evolue.plugins.netty.handlers.monitoring

import io.evolue.api.context.StepContext
import io.evolue.plugins.netty.monitoring.MetricsRecorder
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.ssl.SslHandshakeCompletionEvent

/**
 * Channel handler to monitor the TLS handshake.
 *
 * All the credits go to the project https://github.com/reactor/reactor-netty under the Apache License 2.0.
 *
 * @author Eric Jessé
 */
internal class TlsMonitoringHandler(
    private val metricsRecorder: MetricsRecorder,
    private val connectionStepContext: StepContext<*, *>
) : ChannelInboundHandlerAdapter() {

    private var tlsHandshakeTimeStart: Long = Long.MIN_VALUE

    private var handshakeDone: Boolean = false

    override fun channelActive(ctx: ChannelHandlerContext) {
        ctx.read()
    }

    override fun channelRegistered(ctx: ChannelHandlerContext) {
        tlsHandshakeTimeStart = System.nanoTime()
        super.channelRegistered(ctx)
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        if (!handshakeDone) {
            // Continue consuming.
            ctx.read()
        }
        super.channelReadComplete(ctx)
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is SslHandshakeCompletionEvent) {
            val now = System.nanoTime()
            handshakeDone = true
            // The handler is no longer required.
            ctx.pipeline().context(this)?.let { ctx.pipeline().remove(this) }

            if (tlsHandshakeTimeStart > Long.MIN_VALUE) {
                if (evt.isSuccess) {
                    metricsRecorder.recordSuccessfulTlsHandshakeTime(connectionStepContext, now - tlsHandshakeTimeStart)
                    ctx.fireChannelActive()
                } else {
                    metricsRecorder.recordFailedTlsHandshakeTime(connectionStepContext, now - tlsHandshakeTimeStart)
                    ctx.fireExceptionCaught(evt.cause())
                }
            }
        }
        super.userEventTriggered(ctx, evt)
    }
}