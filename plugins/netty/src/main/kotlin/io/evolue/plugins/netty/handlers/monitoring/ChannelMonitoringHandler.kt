package io.evolue.plugins.netty.handlers.monitoring

import io.evolue.plugins.netty.ClientExecutionContext
import io.evolue.plugins.netty.monitoring.EventsRecorder
import io.evolue.plugins.netty.monitoring.MetricsRecorder
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.channel.socket.DatagramPacket
import java.util.concurrent.atomic.AtomicReference

/**
 * Channel handler to record the channel activity.
 *
 * @author Eric Jessé
 */
internal class ChannelMonitoringHandler(
    private val metricsRecorder: MetricsRecorder,
    private val eventsRecorder: EventsRecorder,
    private val executionContext: AtomicReference<ClientExecutionContext>
) : ChannelDuplexHandler() {

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val execCtx = executionContext.get()
        if (execCtx.eventsConfiguration.receiving) {
            eventsRecorder.recordReceiving(execCtx.stepContext)
        }
        if (execCtx.metricsConfiguration.dataReceived) {
            val size = getMessageSize(msg)
            if (size > 0) {
                metricsRecorder.recordDataReceived(execCtx.stepContext, size)
            }
        }

        ctx.fireChannelRead(msg)
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        val now = System.nanoTime()
        val execCtx = executionContext.get()
        if (execCtx.metricsConfiguration.timeToLastByte) {
            execCtx.getIfPresent<Long>(
                REQUEST_SENT_NANO_TIME)?.apply {
                metricsRecorder.recordTimeToLastByte(execCtx.stepContext, now - this)
            }
        }
        if (execCtx.eventsConfiguration.received) {
            eventsRecorder.recordReceived(execCtx.stepContext)
        }
        ctx.fireChannelReadComplete()
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        val execCtx = executionContext.get()
        if (execCtx.eventsConfiguration.sending) {
            eventsRecorder.recordSending(execCtx.stepContext)
        }

        val size = getMessageSize(msg)
        promise.addListener { result ->
            if (result.isSuccess) {
                val now = System.nanoTime()
                if (execCtx.metricsConfiguration.timeToLastByte) {
                    execCtx.put(
                        REQUEST_SENT_NANO_TIME, now)
                }
                if (execCtx.eventsConfiguration.sent) {
                    eventsRecorder.recordSent(execCtx.stepContext)
                }
                if (execCtx.metricsConfiguration.dataSent && size > 0) {
                    metricsRecorder.recordDataSent(execCtx.stepContext, size)
                }
            }
        }
        super.write(ctx, msg, promise)
    }

    private fun getMessageSize(msg: Any): Int {
        val size = when (msg) {
            is ByteBuf -> msg.readableBytes()
            is DatagramPacket -> msg.content().readableBytes()
            is ByteArray -> msg.size
            else -> 0
        }
        return size
    }

    companion object {

        const val REQUEST_SENT_NANO_TIME = "request-sent-nano-time"

    }
}
